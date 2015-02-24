package org.flexiblepower.protocol.mielegateway.multicast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MulticastListenerThread extends Thread {
    private static final int MAX_BUFFER_SIZE = 1024;

    private static final Logger log = LoggerFactory.getLogger(MulticastListenerThread.class);

    private final InetAddress address;
    private final int port;

    private volatile boolean running;

    public MulticastListenerThread(String name, String address, int port) {
        super(name);
        try {
            this.address = InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            // This should never happen, the address should not be entered by the user
            throw new RuntimeException(e);
        }
        this.port = port;
        running = true;

        start();
    }

    public void close() {
        running = false;
    }

    @Override
    public void run() {
        int retry = 5;

        MulticastSocket socket = null;
        byte[] buffer = new byte[MAX_BUFFER_SIZE];

        while (running) {
            if (socket == null) {
                try {
                    socket = new MulticastSocket(port);
                    socket.joinGroup(address);

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
                DatagramPacket p = new DatagramPacket(buffer, MAX_BUFFER_SIZE);
                socket.receive(p);
                String data = new String(buffer, p.getOffset(), p.getLength());
                handle(data, p.getAddress(), p.getPort());
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

    protected abstract void handle(String data, InetAddress address, int port);
}
