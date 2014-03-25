package de.fraunhofer.itwm.hexabus;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Calendar;

public class HexabusEndpoint {
    private final HexabusDevice device;
    private final int eid;
    private final Hexabus.DataType dataType;
    private final String description;

    public HexabusEndpoint(HexabusDevice device, int eid, Hexabus.DataType dataType, String description) {
        this.device = device;
        this.eid = eid;
        this.dataType = dataType;
        this.description = description;
    }

    public HexabusEndpoint(HexabusDevice device, int eid, Hexabus.DataType dataType) {
        this.device = device;
        this.eid = eid;
        this.dataType = dataType;
        description = "";
    }

    public int getEid() {
        return eid;
    }

    public String getDescription() {
        return description;
    }

    public Hexabus.DataType getDataType() {
        return dataType;
    }

    @Override
    public String toString() {
        return eid + " " + description + " " + dataType;
    }

    public void writeEndpoint(boolean value) throws IOException {
        if (dataType != Hexabus.DataType.BOOL) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected.");
        }
        device.sendPacket(new HexabusWritePacket(eid, value));
    }

    public void writeEndpoint(String value) throws IOException {
        if (dataType != Hexabus.DataType.STRING) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected");
        }
        HexabusPacket packet = new HexabusWritePacket(eid, value);
        device.sendPacket(packet);
    }

    public void writeEndpoint(short value) throws IOException {
        if (dataType != Hexabus.DataType.UINT8) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected");
        }
        HexabusPacket packet = new HexabusWritePacket(eid, value);
        device.sendPacket(packet);
    }

    public void writeEndpoint(float value) throws IOException {
        if (dataType != Hexabus.DataType.FLOAT) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected");
        }
        HexabusPacket packet = new HexabusWritePacket(eid, value);
        device.sendPacket(packet);
    }

    public void writeEndpoint(long value) throws IOException {
        if (dataType != Hexabus.DataType.UINT32) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected");
        }
        HexabusPacket packet = new HexabusWritePacket(eid, value);
        device.sendPacket(packet);
    }

    public void writeEndpoint(Timestamp value) throws IOException {
        if (dataType != Hexabus.DataType.TIMESTAMP) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected");
        }
        HexabusPacket packet = new HexabusWritePacket(eid, value);
        device.sendPacket(packet);
    }

    public void writeEndpoint(Calendar value) throws IOException {
        if (dataType != Hexabus.DataType.DATETIME) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected");
        }
        HexabusPacket packet = new HexabusWritePacket(eid, value);
        device.sendPacket(packet);
    }

    // TODO use setSoTimeout
    public boolean queryBoolEndpoint() throws IOException {
        if (dataType != Hexabus.DataType.BOOL) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected.");
        }
        HexabusPacket packet = new HexabusQueryPacket(eid);
        packet = device.query(packet);
        switch (packet.getPacketType()) {
        case ERROR:
            throw new IllegalArgumentException("Error packet received: " + ((HexabusErrorPacket) packet).getErrorCode());
        case INFO:
            return ((HexabusInfoPacket) packet).getBool();
        default:
            throw new IllegalArgumentException("Unexpected reply received");
        }
    }

    public String queryStringEndpoint() throws IOException {
        if (dataType != Hexabus.DataType.STRING) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected");
        }
        HexabusPacket packet = new HexabusQueryPacket(eid);
        packet = device.query(packet);
        switch (packet.getPacketType()) {
        case ERROR:
            throw new IllegalArgumentException("Error packet received: " + ((HexabusErrorPacket) packet).getErrorCode());
        case INFO:
            return ((HexabusInfoPacket) packet).getString();
        default:
            throw new IllegalArgumentException("Unexpected reply received");
        }
    }

    public short queryUint8Endpoint() throws IOException {
        if (dataType != Hexabus.DataType.UINT8) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected");
        }
        HexabusPacket packet = new HexabusQueryPacket(eid);
        packet = device.query(packet);
        switch (packet.getPacketType()) {
        case ERROR:
            throw new IllegalArgumentException("Error packet received: " + ((HexabusErrorPacket) packet).getErrorCode());
        case INFO:
            return ((HexabusInfoPacket) packet).getUint8();
        default:
            throw new IllegalArgumentException("Unexpected reply received");
        }
    }

    public float queryFloatEndpoint() throws IOException {
        if (dataType != Hexabus.DataType.FLOAT) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected");
        }
        HexabusPacket packet = new HexabusQueryPacket(eid);
        packet = device.query(packet);
        switch (packet.getPacketType()) {
        case ERROR:
            throw new IllegalArgumentException("Error packet received: " + ((HexabusErrorPacket) packet).getErrorCode());
        case INFO:
            return ((HexabusInfoPacket) packet).getFloat();
        default:
            throw new IllegalArgumentException("Unexpected reply received");
        }
    }

    public long queryUint32Endpoint() throws IOException {
        if (dataType != Hexabus.DataType.UINT32) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected");
        }
        HexabusPacket packet = new HexabusQueryPacket(eid);
        packet = device.query(packet);
        switch (packet.getPacketType()) {
        case ERROR:
            throw new IllegalArgumentException("Error packet received: " + ((HexabusErrorPacket) packet).getErrorCode());
        case INFO:
            return ((HexabusInfoPacket) packet).getUint32();
        default:
            throw new IllegalArgumentException("Unexpected reply received");
        }
    }

    public Timestamp queryTimestampEndpoint() throws IOException {
        if (dataType != Hexabus.DataType.TIMESTAMP) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected");
        }
        HexabusPacket packet = new HexabusQueryPacket(eid);
        packet = device.query(packet);
        switch (packet.getPacketType()) {
        case ERROR:
            throw new IllegalArgumentException("Error packet received: " + ((HexabusErrorPacket) packet).getErrorCode());
        case INFO:
            return ((HexabusInfoPacket) packet).getTimestamp();
        default:
            throw new IllegalArgumentException("Unexpected reply received");
        }
    }

    public Calendar queryDatetimeEndpoint() throws IOException {
        if (dataType != Hexabus.DataType.DATETIME) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected");
        }
        HexabusPacket packet = new HexabusQueryPacket(eid);
        device.sendPacket(packet);
        // Receive reply
        packet = device.receivePacket();
        switch (packet.getPacketType()) {
        case ERROR:
            throw new IllegalArgumentException("Error packet received: " + ((HexabusErrorPacket) packet).getErrorCode());
        case INFO:
            return ((HexabusInfoPacket) packet).getDatetime();
        default:
            throw new IllegalArgumentException("Unexpected reply received");
        }
    }
}
