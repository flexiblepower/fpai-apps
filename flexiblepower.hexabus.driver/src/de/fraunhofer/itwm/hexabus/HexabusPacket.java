package de.fraunhofer.itwm.hexabus;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public abstract class HexabusPacket {
    protected InetAddress sourceAddress;
    protected int sourcePort;
    protected Hexabus.PacketType packetType;

    /**
     * @return The source address where a packet came from
     */
    public InetAddress getSourceAddress() {
        return sourceAddress;
    }

    protected void setSourceAddress(InetAddress address) {
        sourceAddress = address;
    }

    /**
     * @return The source port where a packet came from
     */
    public int getSourcePort() {
        return sourcePort;
    }

    protected void setSourcePort(int port) {
        sourcePort = port;
    }

    /**
     * @return The packet type of this packet
     */
    public Hexabus.PacketType getPacketType() {
        return packetType;
    }

    public DatagramPacket createPacket() {
        ByteBuffer buffer = ByteBuffer.allocate(138);
        buildPacket(buffer);
        return new DatagramPacket(buffer.array(), 0, buffer.position());
    }

    private void buildPacket(ByteBuffer buffer) {
        buffer.order(ByteOrder.BIG_ENDIAN);
        // Header "HX0B"
        buffer.putInt(Hexabus.HEADER);
        // Packet Type
        buffer.put(packetType.convert());
        // Flags
        buffer.put((byte) 0);

        buildPacketContent(buffer);

        buffer.putChar(crc(buffer.array(), 0, buffer.position()));
    }

    protected abstract void buildPacketContent(ByteBuffer buffer);

    public static char crc(byte[] bytes, int offset, int limit) {
        char crc = 0x00; // char is an unsigned 16 bit value
        for (int ix = offset; ix < limit; ix++) {
            byte b = bytes[ix];
            crc ^= (b & 0xff);
            crc = (char) ((crc >> 8) | (crc << 8));
            crc ^= (crc & 0xff00) << 4;
            crc ^= (crc >> 8) >> 4;
            crc ^= (crc & 0xff00) >> 5;
        }
        return crc;
    }

    @Override
    public String toString() {
        return getPacketType() + " [" + getSourceAddress() + "_" + getSourcePort() + "] ";
    }
}
