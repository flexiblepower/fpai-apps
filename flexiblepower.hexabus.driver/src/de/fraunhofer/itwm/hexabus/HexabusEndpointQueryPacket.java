package de.fraunhofer.itwm.hexabus;

public class HexabusEndpointQueryPacket extends HexabusQueryPacket {
    public HexabusEndpointQueryPacket(int eid) {
        super(eid);
        packetType = Hexabus.PacketType.EPQUERY;
    }
}
