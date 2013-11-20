package de.fraunhofer.itwm.hexabus;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO document exceptions, maybe specify them more
//TODO maxeid
public class Hexabus implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(Hexabus.class);

    /** Hexabus port { @value } */
    public final static int PORT = 61616;
    /** Hexabus packet header */
    public final static int HEADER = 0x48583042;

    /** Listener have to extend this class to get packets from the HexabusServer */
    public static abstract class HexabusListener {
        private HexabusServer server;

        /**
         * Registers listener with a HexabusServer
         * 
         * @param server
         *            The HexabusServer to register with
         */
        public void register(HexabusServer server) {
            server.register(this);
            this.server = server;
        }

        /**
         * Unregisters listener from a HexabusServer
         */
        public void unregister() {
            if (server != null) {
                server.unregister(this);
            }
        }

        /** This method gets called by the server whenever it receives a packet. */
        public abstract void handlePacket(HexabusPacket packet);
    }

    private final DatagramSocket socket;
    private final BlockingQueue<HexabusPacket> allReceivedPackets;
    private final Map<InetAddress, BlockingQueue<HexabusPacket>> receivedPackets;
    private final AtomicBoolean running;

    public Hexabus() throws SocketException {
        this(new DatagramSocket());
    }

    public Hexabus(DatagramSocket socket) {
        this.socket = socket;
        allReceivedPackets = new ArrayBlockingQueue<HexabusPacket>(128);
        receivedPackets = new ConcurrentHashMap<InetAddress, BlockingQueue<HexabusPacket>>();

        running = new AtomicBoolean(true);
        new Thread("Hexabus receiver thread") {
            @Override
            public void run() {
                while (running.get()) {
                    try {
                        byte[] data = new byte[138]; // Largest packet: 128string info/write packet
                        DatagramPacket packet = new DatagramPacket(data, data.length);
                        Hexabus.this.socket.receive(packet);
                        HexabusPacket p = parsePacket(packet);

                        logger.debug("Received packet {}", p);

                        BlockingQueue<HexabusPacket> queue = receivedPackets.get(p.getSourceAddress());
                        if (queue == null) {
                            queue = new ArrayBlockingQueue<HexabusPacket>(32);
                            receivedPackets.put(p.getSourceAddress(), queue);
                        }
                        queue.put(p);
                        allReceivedPackets.put(p);
                    } catch (IllegalArgumentException ex) {
                        logger.info("Received illegal packet: " + ex.getMessage(), ex);
                    } catch (IOException ex) {
                        logger.error("I/O error in the receiving thread: {}", ex.getMessage(), ex);
                        break;
                    } catch (InterruptedException e) {
                    }
                }
            };
        }.start();
    }

    /**
     * Receives packet on specified port. Will block until specified timeout exceeds.
     * 
     * @return The received and parsed hexabus packet
     */
    public HexabusPacket receivePacket() throws IOException {
        return receivePacket(null);
    }

    public HexabusPacket receivePacket(InetAddress from) throws IOException {
        try {
            if (from != null) {
                if (!receivedPackets.containsKey(from)) {
                    receivedPackets.put(from, new ArrayBlockingQueue<HexabusPacket>(32));
                }
                HexabusPacket packet = receivedPackets.get(from).poll(10, TimeUnit.SECONDS);
                if (packet != null) {
                    allReceivedPackets.remove(packet);
                    return packet;
                } else {
                    throw new NoResponseException("No response within 10 seconds.");
                }
            } else {
                HexabusPacket packet = allReceivedPackets.poll(10, TimeUnit.SECONDS);
                if (packet != null) {
                    receivedPackets.get(packet.getSourceAddress()).remove(packet);
                    return packet;
                } else {
                    throw new NoResponseException("No response within 10 seconds.");
                }
            }
        } catch (InterruptedException ex) {
            throw new NoResponseException(ex);
        }
    }

    /**
     * Parses a { @link DatagramPacket } and returns extracted { @link HexabusPacket }
     * 
     * @param packet
     *            The packet that should be parsed
     * @return The extracted hexabus packet
     */
    public static HexabusPacket parsePacket(DatagramPacket packet) {
        InetAddress address = packet.getAddress();
        int sourcePort = packet.getPort();
        byte[] data = packet.getData();

        // System.out.println("Parsing " + Arrays.toString(Arrays.copyOfRange(data, packet.getOffset(),
        // packet.getLength())));
        ByteBuffer buffer = ByteBuffer.wrap(data, packet.getOffset(), packet.getLength());
        buffer.order(ByteOrder.BIG_ENDIAN);

        if (buffer.getInt() != HEADER) {
            throw new IllegalArgumentException("Missing header");
        }

        HexabusPacket parsedPacket;

        PacketType packetType = getPacketType(buffer.get());
        buffer.get(); // ignore flags

        DataType dataType;
        int eid;
        // ByteBuffer to get value of info/write packets
        byte[] payload;
        switch (packetType) {
        case ERROR:
            parsedPacket = new HexabusErrorPacket(buffer.get());
            break;
        case INFO:
            eid = buffer.get();
            dataType = getDataType(buffer.get());

            payload = new byte[dataType.getSize()];
            buffer.get(payload);
            parsedPacket = new HexabusInfoPacket(eid, dataType, payload);
            break;
        case QUERY:
            parsedPacket = new HexabusQueryPacket(buffer.get());
            break;
        case WRITE:
            eid = buffer.get();
            dataType = getDataType(buffer.get());

            payload = new byte[dataType.getSize()];
            buffer.get(payload);
            parsedPacket = new HexabusWritePacket(eid, dataType, payload);
            break;
        case EPINFO:
            eid = buffer.get();
            dataType = getDataType(buffer.get());

            payload = new byte[Hexabus.DataType.STRING.getSize()];
            buffer.get(payload);
            parsedPacket = new HexabusEndpointInfoPacket(eid, dataType, payload);
            break;
        case EPQUERY:
            parsedPacket = new HexabusEndpointQueryPacket(buffer.get());
            break;
        default:
            throw new IllegalArgumentException("Unknown packet type");
        }

        char crc = buffer.getChar();
        char calcCrc = HexabusPacket.crc(buffer.array(), 0, buffer.position() - 2);

        if (crc != calcCrc) {
            throw new IllegalArgumentException("CRC error");
        }

        parsedPacket.setSourceAddress(address);
        parsedPacket.setSourcePort(sourcePort);
        return parsedPacket;
    }

    public void sendPacket(HexabusPacket hPacket, InetAddress address, int port) throws IOException {
        // System.out.println("Sending " + hPacket + " to " + address + "_" + port);
        DatagramPacket packet = hPacket.createPacket();
        packet.setAddress(address);
        packet.setPort(port);
        socket.send(packet);
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        socket.close();
    }

    /**
     * Enum for packet types
     */
    public enum PacketType implements EnumConverter {
        ERROR(0, 9), INFO(1, 10), QUERY(2, 9), WRITE(4, 10), EPINFO(9, 10), EPQUERY(0x0A, 9);
        PacketType(int value, int baseLength) {
            this.value = (byte) value;
            this.baseLength = baseLength;
        }

        private final byte value;
        private final int baseLength;

        @Override
        public byte convert() {
            return value;
        }

        public int getBaseLength() {
            return baseLength;
        }
    }

    /**
     * Enum for data types
     */
    public enum DataType implements EnumConverter {
        BOOL(1, 1), UINT8(2, 1), UINT32(3, 4), DATETIME(4, 8), FLOAT(5, 4), STRING(6, 128), TIMESTAMP(7, 4);
        DataType(int value, int size) {
            this.value = (byte) value;
            this.size = size;
        }

        private final byte value;
        private final int size;

        @Override
        public byte convert() {
            return value;
        }

        public int getSize() {
            return size;
        }
    }

    /**
     * Enum for error codes
     */
    public enum ErrorCode implements EnumConverter {
        UNKNOWNEID(1), WRITEREADONLY(2), CRCFAILED(3), DATATYPE(4);
        private final byte value;

        ErrorCode(int value) {
            this.value = (byte) value;
        }

        @Override
        public byte convert() {
            return value;
        }
    }

    // TODO: error handling in parse functions

    /**
     * Parses a byte array and extracts a boolean value from the first field. Expects the value of the field to be
     * either 0 or 1.
     * 
     * @param value
     *            The byte array that should be parsed
     * @return The extracted value
     */
    public static boolean parseBool(byte[] value) {
        // if(value.length > 1) {...
        switch (value[0]) {
        case 0:
            return false;
        case 1:
            return true;
        default:
            return true;
            // throw new IllegalArgumentException("Wrong data type. BOOL expected, not " + value[0]);
        }
    }

    /**
     * Parses a byte array and extracts a short value representing a uint_8 value.
     * 
     * @param value
     *            The byte array that should be parsed
     * @return The extracted value
     */
    public static short parseUint8(byte[] value) {
        return (short) (value[0] & 0xFF);
    }

    /**
     * Parses a byte array and extracts a long value representing a uint_32 value.
     * 
     * @param value
     *            The byte array that should be parsed
     * @return The extracted value
     */
    public static long parseUint32(byte[] value) {
        return (value[0] << 24 | value[1] << 16 | value[2] << 8 | value[3]) & 0xFFFFFFFFL;
    }

    /**
     * Parses a byte array and extracts a float value.
     * 
     * @param value
     *            The byte array that should be parsed
     * @return The extracted value
     */
    public static float parseFloat(byte[] value) {
        ByteBuffer buffer = ByteBuffer.wrap(value);
        buffer.order(ByteOrder.BIG_ENDIAN);
        return buffer.getFloat();

    }

    /**
     * Parses a byte array and extracts a String value.
     * 
     * @param value
     *            The byte array that should be parsed
     * @return The extracted value
     */
    public static String parseString(byte[] value) {
        return new String(value);
    }

    /**
     * Parses a byte array and extracts a { @link Timestamp } value.
     * 
     * @param value
     *            The byte array that should be parsed
     * @return The extracted value
     */
    public static Timestamp parseTimestamp(byte[] value) {
        long timestamp = (value[0] << 24 | value[1] << 16 | value[2] << 8 | value[3]) & 0xFFFFFFFFL;
        return new Timestamp(timestamp);
    }

    /**
     * Parses a byte array and extracts a { @link Calendar } value representing a datetime value.
     * 
     * @param value
     *            The byte array that should be parsed
     * @return The extracted value
     */
    public static Calendar parseDatetime(byte[] value) {
        ByteBuffer buffer = ByteBuffer.wrap(value);
        buffer.order(ByteOrder.BIG_ENDIAN);
        int hour = buffer.get();
        int minute = buffer.get();
        int second = buffer.get();
        int day = buffer.get();
        int month = buffer.get() - 1;
        int year = buffer.get() << 8 | buffer.get();
        int weekday = buffer.get();

        Calendar calendar = new GregorianCalendar();
        calendar.add(Calendar.HOUR_OF_DAY, hour);
        calendar.add(Calendar.MINUTE, minute);
        calendar.add(Calendar.SECOND, second);
        calendar.add(Calendar.DAY_OF_MONTH, day);
        calendar.add(Calendar.MONTH, month);
        calendar.add(Calendar.YEAR, year);
        calendar.add(Calendar.DAY_OF_WEEK, weekday);

        return calendar;
    }

    public static ErrorCode getErrorCode(byte code) {
        ReverseEnumMap<ErrorCode> reverse = new ReverseEnumMap<ErrorCode>(ErrorCode.class);
        return reverse.get(code);
    }

    public static PacketType getPacketType(byte type) {
        ReverseEnumMap<PacketType> reverse = new ReverseEnumMap<PacketType>(PacketType.class);
        return reverse.get(type);
    }

    public static DataType getDataType(byte type) {
        ReverseEnumMap<DataType> reverse = new ReverseEnumMap<DataType>(DataType.class);
        return reverse.get(type);
    }

    // See: http://www.javaspecialists.co.za/archive/Issue113.html
    private interface EnumConverter {
        public byte convert();
    }

    private static class ReverseEnumMap<V extends Enum<V> & EnumConverter> {
        private final Map<Byte, V> map = new HashMap<Byte, V>();

        public ReverseEnumMap(Class<V> valueType) {
            for (V v : valueType.getEnumConstants()) {
                map.put(v.convert(), v);
            }
        }

        public V get(byte num) {
            return map.get(num);
        }
    }
}
