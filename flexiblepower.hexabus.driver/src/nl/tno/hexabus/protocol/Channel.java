package nl.tno.hexabus.protocol;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Channel implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(Channel.class);

    // 128 (max content) + 9 (header) bytes
    public static final int MAX_PACKET_SIZE = 138;

    private static final InetAddress MCAST_ADDR;
    static {
        try {
            MCAST_ADDR = InetAddress.getByName("ff02::1");
        } catch (UnknownHostException ex) {
            throw new Error("Should not be possible", ex);
        }
    }

    public interface Communicator {
        boolean handlePacket(Packet packet, Channel channel);

        Packet getNextRequest();
    }

    private final class ChannelThread extends Thread {
        private final int timeout;

        public ChannelThread(int timeout) {
            super("Hexabus Channel thread");
            this.timeout = timeout;
        }

        @Override
        public synchronized void run() {
            int retry = 5;

            while (running.get()) {
                if (socket == null) {
                    try {
                        // NetworkInterface iface = detectIface();
                        // InetAddress inetAddress = detectAddress(iface);
                        // if (inetAddress == null) {
                        // log.warn("No Hexabus interface found! Trying to work on all interfaces");
                        socket = new MulticastSocket(61616);
                        // } else {
                        // socket = new MulticastSocket(new InetSocketAddress(inetAddress, 61616));
                        // }

                        // if (iface != null) {
                        // socket.setNetworkInterface(iface);
                        // } else {
                        // log.warn("No HEXABUS interface found! Trying to work on all interfaces");
                        // }
                        socket.joinGroup(MCAST_ADDR);
                        socket.setSoTimeout(timeout);

                        retry = 5;
                        log.debug("Opened the MulticastSocket on {}", socket.getLocalSocketAddress());
                    } catch (IOException ex) {
                        socket = null;

                        log.warn("Error while opening multicast socket, trying again in " + retry + " seconds", ex);
                        try {
                            wait(retry * 1000);
                        } catch (InterruptedException e) {
                        }
                        retry *= 2;
                        continue;
                    }
                }

                try {
                    pendingRequests.set(false);
                    for (DatagramPacket p : getPendingPackets()) {
                        socket.send(p);
                    }

                    DatagramPacket p = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
                    while (running.get() && !pendingRequests.get()) {
                        socket.receive(p);
                        pendingRequests.set(receivedData(p));
                    }
                } catch (SocketTimeoutException ex) {
                    // Expected, just loop and try again
                    continue;
                } catch (IOException ex) {
                    log.error("I/O error while receiving data, trying to restart the socket", ex);
                    socket.close();
                    socket = null;
                    continue;
                }
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
                socket = null;
            }
        }
    }

    // private static NetworkInterface detectIface() throws SocketException {
    // Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
    // NetworkInterface iface = null;
    // while (interfaces.hasMoreElements()) {
    // iface = interfaces.nextElement();
    // log.debug("Testing interface with name {}", iface.getDisplayName());
    // if (iface.supportsMulticast() && (iface.getDisplayName().contains("HEXABUS") || iface.getDisplayName()
    // .contains("usb"))) {
    // log.debug("Using interface {}", iface);
    // break;
    // }
    // iface = null;
    // }
    // return iface;
    // }
    //
    // private static InetAddress detectAddress(NetworkInterface iface) {
    // if (iface != null) {
    // Enumeration<InetAddress> addresses = iface.getInetAddresses();
    // log.debug("Detecting the IP addresses");
    // while (addresses.hasMoreElements()) {
    // InetAddress address = addresses.nextElement();
    // byte[] raw = address.getAddress();
    // if (raw.length == 16 && raw[0] == (byte) 0xfe && raw[1] == (byte) 0x80) {
    // log.debug("Using {}", address);
    // return address;
    // } else {
    // log.debug("Not using {} (length = {})", address, raw.length);
    // }
    // }
    // }
    // return null;
    // }

    private final PacketCodec codec;

    private final Communicator restListener;
    private final Map<Inet6Address, Communicator> listeners;

    final AtomicBoolean running, pendingRequests;
    private final Thread readThread;

    private volatile MulticastSocket socket;

    public Channel(Communicator restListener) {
        codec = new PacketCodec();
        this.restListener = restListener;
        listeners = new ConcurrentHashMap<Inet6Address, Communicator>();
        running = new AtomicBoolean(true);
        pendingRequests = new AtomicBoolean(false);

        readThread = new ChannelThread(3000);
    }

    public void open() {
        running.set(true);
        readThread.start();
    }

    @Override
    public void close() {
        running.set(false);
        readThread.interrupt();
    }

    public synchronized void registerListener(Inet6Address address, Communicator listener) {
        if (!listeners.containsKey(address)) {
            listeners.put(address, listener);
        }
    }

    public synchronized void removeListener(Inet6Address address, Communicator listener) {
        if (listeners.get(address) == listener) {
            listeners.remove(address);
        }
    }

    List<DatagramPacket> getPendingPackets() {
        List<DatagramPacket> result = new ArrayList<DatagramPacket>();
        for (Communicator c : listeners.values()) {
            Packet packet = c.getNextRequest();
            if (packet != null) {
                ByteBuffer buffer = ByteBuffer.allocate(MAX_PACKET_SIZE);
                codec.write(packet, buffer);
                result.add(new DatagramPacket(buffer.array(), buffer.limit(), packet.getAddress(), packet.getPort()));
            }
        }
        return result;
    }

    boolean receivedData(DatagramPacket dp) {
        ByteBuffer buffer = ByteBuffer.wrap(dp.getData(), dp.getOffset(), dp.getLength());
        Packet packet = codec.read((Inet6Address) dp.getAddress(), dp.getPort(), buffer);
        if (packet != null) {
            Communicator listener = listeners.get(dp.getAddress());
            if (listener != null) {
                return listener.handlePacket(packet, this);
            } else {
                return restListener.handlePacket(packet, this);
            }
        } else {
            buffer.rewind();
            log.debug("Received incorrect or corrupt packet: {}", new PacketCodec.HexDumper(buffer));
            return false;
        }
    }

    public static void main(String[] args) throws IOException {
        final Channel channel = new Channel(new Communicator() {
            @Override
            public boolean handlePacket(Packet packet, Channel channel) {
                Device device = new Device(packet.getAddress(), packet.getPort());
                channel.registerListener(packet.getAddress(), device);
                return device.handlePacket(packet, channel);
            }

            @Override
            public Packet getNextRequest() {
                return null;
            }
        });

        channel.open();
        System.in.read();
        channel.close();
    }
}
