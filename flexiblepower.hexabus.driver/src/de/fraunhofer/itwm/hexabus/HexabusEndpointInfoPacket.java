package de.fraunhofer.itwm.hexabus;

public class HexabusEndpointInfoPacket extends HexabusInfoPacket {
    public HexabusEndpointInfoPacket(int eid, Hexabus.DataType dataType, byte[] payload) {
        super(eid, Hexabus.DataType.STRING, payload);
        packetType = Hexabus.PacketType.EPINFO;
        this.dataType = dataType;
    }

    public String getDescription() {
        return Hexabus.parseString(payload);
    }

    @Override
    public Object getData() {
        return getString();
    }

    @Override
    public String getString() {
        return Hexabus.parseString(payload);
    }
}
