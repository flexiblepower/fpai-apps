package de.fraunhofer.itwm.hexabus;

import java.nio.ByteBuffer;

public class HexabusQueryPacket extends HexabusPacket {
    private final byte eid;

    public HexabusQueryPacket(int eid) {
        packetType = Hexabus.PacketType.QUERY;
        if (eid > 255) {
            throw new IllegalArgumentException("EID too large");
        }
        this.eid = (byte) eid;
    }

    @Override
    protected void buildPacketContent(ByteBuffer buffer) {
        buffer.put(eid);
    }

    @Override
    public String toString() {
        return super.toString() + eid;
    }
}
