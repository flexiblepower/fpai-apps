package de.fraunhofer.itwm.hexabus;

import java.nio.ByteBuffer;

public class HexabusErrorPacket extends HexabusPacket {
    private final byte errorCode;

    public HexabusErrorPacket(Hexabus.ErrorCode errorCode) {
        packetType = Hexabus.PacketType.ERROR;
        this.errorCode = errorCode.convert();
    }

    public HexabusErrorPacket(byte errorCode) {
        packetType = Hexabus.PacketType.ERROR;
        this.errorCode = errorCode;
    }

    public Hexabus.ErrorCode getErrorCode() {
        return Hexabus.getErrorCode(errorCode);
    }

    @Override
    protected void buildPacketContent(ByteBuffer buffer) {
        buffer.put(errorCode);
    }

    @Override
    public String toString() {
        return super.toString() + errorCode;
    }
}
