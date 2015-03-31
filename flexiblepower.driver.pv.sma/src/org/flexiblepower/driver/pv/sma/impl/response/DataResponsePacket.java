package org.flexiblepower.driver.pv.sma.impl.response;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.flexiblepower.driver.pv.sma.impl.L2Packet;
import org.flexiblepower.driver.pv.sma.impl.utils.ByteUtils;

public class DataResponsePacket extends L2Packet {
    private static Map<Integer, Code> reverseCodeMap;
    private static HashMap<Integer, DataType> reverseDataTypeMap;

    static {
        reverseDataTypeMap = new HashMap<Integer, DataType>();
        for (DataType dt : DataType.values()) {
            reverseDataTypeMap.put(dt.code, dt);
        }

        reverseCodeMap = new HashMap<Integer, Code>();
        for (Code c : Code.values()) {
            reverseCodeMap.put(c.code, c);
        }
    }

    public static enum Code {
        PROD_TODAY(0x262201), PROD_LIFETIME(0x260101), SPOT_AC_POWER(0x263F01), SPOT_AC_FREQUENCY(0x465701), OPERATION_TIME(0x462E01), OPERATION_FEEDIN_TIME(0x462F01);

        private final int code;

        private Code(int code) {
            this.code = code;
            reverseCodeMap.put(code, this);
        }

        public int getCode() {
            return code;
        }
    }

    public static enum DataType {
        LONG(0x00), STATUS(0x08), TEXT(0x10), INT(0x40);

        private final int code;

        private DataType(int code) {
            this.code = code;
            reverseDataTypeMap.put(code, this);
        }

        public int getCode() {
            return code;
        }
    }

    public static class Element {
        private final Code code;
        private final BigDecimal value;
        private final Date timestamp;

        public Element(Code code, BigDecimal value, Date timestamp) {
            super();
            this.code = code;
            this.value = value;
            this.timestamp = timestamp;
        }

        public Date getTimestamp() {
            return timestamp;
        }

        public Code getCode() {
            return code;
        }

        public BigDecimal getValue() {
            return value;
        }

        @Override
        public String toString() {
            return code + ": " + value + " (" + timestamp + ")";
        }
    }

    public static DataResponsePacket parse(L2Packet packet) throws ParseException {
        ByteBuffer buffer = ByteUtils.createBuffer();
        buffer.put(packet.getData());
        buffer.rewind();
        
        Map<Code, Element> elements = new EnumMap<Code, Element>(Code.class);
        buffer.getLong(); // Skip first 8 bytes

        while (buffer.remaining() >= 8) {
            int type = buffer.getInt();
            DataType dataType = reverseDataTypeMap.get((type >> 24) & 0xff);
            Code codeType = reverseCodeMap.get(type & 0xFFFFFF);
            Date timestamp = new Date(buffer.getInt() * 1000l);

            if (codeType == null) {
                if (dataType == DataType.LONG) {
                    buffer.getLong();
                } else if (dataType == DataType.INT) {
                    buffer.getInt();
                } else {
                    throw new ParseException("Unknown data type: " + ByteUtils.toHexString(type), buffer.position());
                }
            } else {
                BigDecimal value;
                switch (codeType) {
                case PROD_TODAY:
                case PROD_LIFETIME:
                    value = BigDecimal.valueOf(buffer.getLong(), 3);
                    elements.put(codeType, new Element(codeType, value, timestamp));
                    break;
                case SPOT_AC_POWER:
                    value = BigDecimal.valueOf(buffer.getInt(), 0);
                    elements.put(codeType, new Element(codeType, value, timestamp));
                    break;
                case SPOT_AC_FREQUENCY:
                	value = BigDecimal.valueOf(buffer.getInt(), 2);
                	elements.put(codeType, new Element(codeType, value, timestamp));
                	break;
                case OPERATION_TIME:
                case OPERATION_FEEDIN_TIME:
                	value = BigDecimal.valueOf(buffer.getLong(), 0);
                	elements.put(codeType, new Element(codeType, value, timestamp));
                }
            }
        }

        return new DataResponsePacket(packet, elements);
    }

    private final Map<Code, Element> elements;

    public DataResponsePacket(L2Packet packet, Map<Code, Element> elements) {
        super(packet);
        this.elements = Collections.unmodifiableMap(elements);
    }

    public Map<Code, Element> getElements() {
        return elements;
    }

    @Override
    public String toString() {
        return super.toString() + " => " + elements;
    }
}
