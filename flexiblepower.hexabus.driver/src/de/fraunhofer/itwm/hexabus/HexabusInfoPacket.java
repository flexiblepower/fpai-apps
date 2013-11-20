package de.fraunhofer.itwm.hexabus;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.util.Calendar;

public class HexabusInfoPacket extends HexabusPacket {

    protected Hexabus.DataType dataType;
    protected byte[] payload;
    protected byte eid;

    public HexabusInfoPacket(int eid, Hexabus.DataType dataType, byte[] payload) {
        packetType = Hexabus.PacketType.INFO;
        if (eid > 255) {
            throw new IllegalArgumentException("EID too large");
        }
        this.eid = (byte) eid;
        this.dataType = dataType;
        if (payload.length > dataType.getSize()) {
            throw new IllegalArgumentException("Payload too large");
        }
        this.payload = payload;
    }

    public HexabusInfoPacket(int eid, boolean value) {
        packetType = Hexabus.PacketType.INFO;
        if (eid > 255) {
            throw new IllegalArgumentException("EID too large");
        }
        this.eid = (byte) eid;
        dataType = Hexabus.DataType.BOOL;
        payload = new byte[] { (byte) (value ? 0x01 : 0x00) };
    }

    public HexabusInfoPacket(int eid, float value) {
        packetType = Hexabus.PacketType.INFO;
        if (eid > 255) {
            throw new IllegalArgumentException("EID too large");
        }
        this.eid = (byte) eid;
        dataType = Hexabus.DataType.FLOAT;
        payload = new byte[4];
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putFloat(value);
    }

    public HexabusInfoPacket(int eid, short value) {
        packetType = Hexabus.PacketType.INFO;
        if (eid > 255) {
            throw new IllegalArgumentException("EID too large");
        }
        this.eid = (byte) eid;
        dataType = Hexabus.DataType.UINT8;
        // uint8 value as short
        if (value > 255) {
            throw new IllegalArgumentException("Value too large. UINT8 expected.");
        }
        payload = new byte[] { (byte) (value & 0xFF) };
    }

    public HexabusInfoPacket(int eid, long value) {
        packetType = Hexabus.PacketType.INFO;
        if (eid > 255) {
            throw new IllegalArgumentException("EID too large");
        }
        this.eid = (byte) eid;
        dataType = Hexabus.DataType.UINT32;
        payload = new byte[] { (byte) ((value & 0xFF000000L) >> 24),
                              (byte) ((value & 0x00FF0000L) >> 16),
                              (byte) ((value & 0x0000FF00L) >> 8),
                              (byte) (value & 0x000000FFL) };
    }

    public HexabusInfoPacket(int eid, Timestamp value) {
        packetType = Hexabus.PacketType.INFO;
        if (eid > 255) {
            throw new IllegalArgumentException("EID too large");
        }
        this.eid = (byte) eid;
        dataType = Hexabus.DataType.TIMESTAMP;
        long timestamp = value.getTime();
        payload = new byte[] { (byte) ((timestamp & 0xFF000000L) >> 24),
                              (byte) ((timestamp & 0x00FF0000L) >> 16),
                              (byte) ((timestamp & 0x0000FF00L) >> 8),
                              (byte) (timestamp & 0x000000FFL) };
    }

    public HexabusInfoPacket(int eid, Calendar value) {
        packetType = Hexabus.PacketType.INFO;
        if (eid > 255) {
            throw new IllegalArgumentException("EID too large");
        }
        this.eid = (byte) eid;
        dataType = Hexabus.DataType.DATETIME;
        payload = new byte[dataType.getSize()];
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) (value.get(Calendar.HOUR_OF_DAY) & 0xFF));
        buffer.put((byte) (value.get(Calendar.MINUTE) & 0xFF));
        buffer.put((byte) (value.get(Calendar.SECOND) & 0xFF));
        buffer.put((byte) (value.get(Calendar.DAY_OF_MONTH) & 0xFF));
        buffer.put((byte) ((value.get(Calendar.MONTH) + 1) & 0xFF));
        buffer.put((byte) ((value.get(Calendar.YEAR) & 0xFF00) >> 8));
        buffer.put((byte) (value.get(Calendar.YEAR) & 0x00FF));
        buffer.put((byte) ((value.get(Calendar.DAY_OF_WEEK) - 1) & 0xFF));
    }

    public HexabusInfoPacket(int eid, String value) {
        packetType = Hexabus.PacketType.INFO;
        if (eid > 255) {
            throw new IllegalArgumentException("EID too large");
        }
        if (value.length() > 127) {
            throw new IllegalArgumentException("String too long");
            // check charset
        } else if (!Charset.forName("US-ASCII").newEncoder().canEncode(value)) {
            throw new IllegalArgumentException("Non-ascii string");
        }

        this.eid = (byte) eid;
        dataType = Hexabus.DataType.STRING;
        payload = new byte[dataType.getSize()];
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        buffer.order(ByteOrder.BIG_ENDIAN);
        try {
            buffer.put(value.getBytes("US-ASCII"));
        } catch (Exception e) {
            throw new IllegalArgumentException("Non-existing charset. Please report to author.");
        }// TODO

        for (int i = 0; i <= (127 - value.length()); i++) { // Fill payload field with zeros
            buffer.put((byte) 0x00);
        }
    }

    public Hexabus.DataType getDataType() {
        return dataType;
    }

    public int getEid() {
        return eid;
    }

    public Object getData() {
        if (dataType == null) {
            return getString();
        }
        switch (dataType) {
        case BOOL:
            return getBool();
        case DATETIME:
            return getDatetime();
        case FLOAT:
            return getFloat();
        case STRING:
            return getString();
        case TIMESTAMP:
            return getTimestamp();
        case UINT32:
            return getUint32();
        case UINT8:
            return getUint8();
        default:
            throw new IllegalArgumentException("Unknown type");
        }
    }

    public boolean getBool() {
        if (dataType != Hexabus.DataType.BOOL) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected.");
        }
        return Hexabus.parseBool(payload);
    }

    public short getUint8() {
        if (dataType != Hexabus.DataType.UINT8) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected.");
        }
        return Hexabus.parseUint8(payload);
    }

    public String getString() {
        if (dataType != Hexabus.DataType.STRING) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected.");
        }
        return Hexabus.parseString(payload);
    }

    public float getFloat() {
        if (dataType != Hexabus.DataType.FLOAT) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected.");
        }
        return Hexabus.parseFloat(payload);
    }

    public long getUint32() {
        if (dataType != Hexabus.DataType.UINT32) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected.");
        }
        return Hexabus.parseUint32(payload);
    }

    public Timestamp getTimestamp() {
        if (dataType != Hexabus.DataType.TIMESTAMP) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected.");
        }
        return Hexabus.parseTimestamp(payload);
    }

    public Calendar getDatetime() {
        if (dataType != Hexabus.DataType.DATETIME) {
            throw new IllegalArgumentException("Wrong data type. " + dataType + " expected.");
        }
        return Hexabus.parseDatetime(payload);
    }

    @Override
    protected void buildPacketContent(ByteBuffer buffer) {
        buffer.put(eid);
        buffer.put(dataType.convert());
        buffer.put(payload);
    }

    @Override
    public String toString() {
        return super.toString() + ": " + eid + "/" + dataType + " " + getData();
    }
}
