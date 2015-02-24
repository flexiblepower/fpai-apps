package nl.tno.hexabus.protocol;

import java.net.Inet6Address;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.GregorianCalendar;

import nl.tno.hexabus.protocol.Packet.Write;
import nl.tno.hexabus.protocol.TypeMap.Coded;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacketCodec {
    public static class HexDumper {
        private static final char[] hex = "0123456789abcdef".toCharArray();

        private final ByteBuffer buffer;

        public HexDumper(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder((buffer.limit() - buffer.position()) * 2);
            for (int i = buffer.position(); i < buffer.limit(); i++) {
                byte b = buffer.get(i);
                sb.append(hex[(b >> 4) & 0xf]);
                sb.append(hex[b & 0xf]);
            }
            return sb.toString();
        }
    }

    public static final int HEADER = 0x48583042;

    public static final Charset ASCII = Charset.forName("US-ASCII");

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

    private static final Logger log = LoggerFactory.getLogger(PacketCodec.class);

    public Packet read(Inet6Address address, int port, ByteBuffer buffer) {
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.rewind();

        log.trace("Decoding packet from {}_{}: {}", address, port, new HexDumper(buffer));

        int header = buffer.getInt();
        if (header != HEADER) {
            log.trace("Illegal header: {} != {}", header, HEADER);
            return null;
        }

        byte typeId = buffer.get();
        Class<? extends Packet> type = Packet.types.get(typeId);
        if (type == null) {
            log.trace("Unknown type: {}", typeId);
            return null;
        }

        buffer.get(); // Ignore the flags for now
        Packet packet = null;

        if (type == Packet.EndpointInfo.class) {
            byte eid = buffer.get();
            byte dataTypeId = buffer.get();
            Class<? extends Data> dataType = Data.types.get(dataTypeId);
            if (dataType != null) {
                packet = new Packet.EndpointInfo(address, port, dataType, new Data.Text(eid, parseText(buffer)));
            } else {
                log.trace("Unknown data type {}", dataTypeId);
            }
        } else if (type == Packet.EndpointQuery.class) {
            byte eid = buffer.get();
            packet = new Packet.EndpointQuery(address, port, eid);
        } else if (type == Packet.Error.class) {
            byte codeId = buffer.get();
            Packet.Error.Code code = Packet.Error.codes.get(codeId);
            if (code != null) {
                packet = new Packet.Error(address, port, code);
            } else {
                log.trace("Unknown code type {}", codeId);
            }
        } else if (type == Packet.Info.class) {
            packet = new Packet.Info(address, port, readData(buffer));
        } else if (type == Packet.Query.class) {
            packet = new Packet.Query(address, port, buffer.get());
        } else if (type == Packet.Write.class) {
            packet = new Packet.Write(address, port, readData(buffer));
        }

        // Check CRC
        char receivedCrc = buffer.getChar();
        char calculatedCrc = crc(buffer.array(), 0, buffer.limit() - 2);

        if (receivedCrc != calculatedCrc) {
            log.trace("Crc error, calculated {}, received {}", (int) calculatedCrc, (int) receivedCrc);
            return null;
        } else {
            log.trace("Parsed result {}", packet);
            return packet;
        }
    }

    Data readData(ByteBuffer buffer) {
        byte eid = buffer.get();
        Class<? extends Data> type = Data.types.get(buffer.get());
        if (type == Data.Bool.class) {
            return new Data.Bool(eid, buffer.get() != 0);
        } else if (type == Data.DateTime.class) {
            return new Data.DateTime(eid, parseDatetime(buffer));
        } else if (type == Data.Float.class) {
            return new Data.Float(eid, buffer.getFloat());
        } else if (type == Data.Text.class) {
            return new Data.Text(eid, parseText(buffer));
        } else if (type == Data.Timestamp.class) {
            return new Data.Timestamp(eid, buffer.getLong());
        } else if (type == Data.UInt32.class) {
            return new Data.UInt32(eid, buffer.getInt() & 0xffffffffL);
        } else if (type == Data.UInt8.class) {
            return new Data.UInt8(eid, buffer.get() & 0xff);
        } else {
            throw new AssertionError("Contact the author, this should never happen");
        }
    }

    private String parseText(ByteBuffer buffer) {
        int oldLimit = buffer.limit();
        buffer.limit(buffer.position() + 128);

        try {
            return ASCII.newDecoder().decode(buffer).toString().trim();
        } catch (CharacterCodingException e) {
            return "";
        } finally {
            buffer.limit(oldLimit);
        }
    }

    private Calendar parseDatetime(ByteBuffer buffer) {
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

    public void write(final Packet packet, final ByteBuffer buffer) {
        log.trace("Writing packet {}", packet);
        byte type = getType(packet.getClass());

        buffer.clear();
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(HEADER);
        buffer.put(type);
        buffer.put((byte) 0);

        packet.visit(new Packet.Visitor() {
            @Override
            public void visit(Packet.EndpointInfo packet) {
                byte type = getType(packet.getEndpointType());
                write(type, packet.getData(), buffer);
            }

            @Override
            public void visit(Packet.EndpointQuery packet) {
                buffer.put(packet.getEid());
            }

            @Override
            public void visit(Packet.Error packet) {
                buffer.put(packet.getErrorCode().getValue());
            }

            @Override
            public void visit(Packet.Info packet) {
                byte type = getType(packet.getData().getClass());
                write(type, packet.getData(), buffer);
            }

            @Override
            public void visit(Packet.Query packet) {
                buffer.put(packet.getEid());
            }

            @Override
            public void visit(Write packet) {
                byte type = getType(packet.getData().getClass());
                write(type, packet.getData(), buffer);
            }
        });

        buffer.putChar(PacketCodec.crc(buffer.array(), 0, buffer.position()));
        buffer.flip();

        log.trace("Written packet {}", new HexDumper(buffer));
    }

    private byte getType(final Class<?> clazz) {
        Coded coded = clazz.getAnnotation(Coded.class);
        if (coded == null) {
            throw new IllegalArgumentException("Trying to send a packet with an unknown type (missing the @Coded annotation for " + clazz.getName()
                                               + ")");
        }
        return coded.value();
    }

    void write(byte type, final Data data, final ByteBuffer buffer) {
        buffer.put(data.getEid());
        buffer.put(type);

        data.visit(new Data.Visitor<Void>() {
            @Override
            public Void visit(Data.Timestamp data) {
                buffer.putLong(data.get());
                return null;
            }

            @Override
            public Void visit(Data.Text data) {
                int end = buffer.position() + 128;
                ASCII.newEncoder().encode(CharBuffer.wrap(data.getValue()), buffer, true);
                while (buffer.position() < end) {
                    buffer.put((byte) 0);
                }
                return null;
            }

            @Override
            public Void visit(Data.Float data) {
                buffer.putFloat(data.get());
                return null;
            }

            @Override
            public Void visit(Data.DateTime data) {
                Calendar c = data.getValue();
                buffer.put((byte) (c.get(Calendar.HOUR_OF_DAY) & 0xFF));
                buffer.put((byte) (c.get(Calendar.MINUTE) & 0xFF));
                buffer.put((byte) (c.get(Calendar.SECOND) & 0xFF));
                buffer.put((byte) (c.get(Calendar.DAY_OF_MONTH) & 0xFF));
                buffer.put((byte) ((c.get(Calendar.MONTH) + 1) & 0xFF));
                buffer.put((byte) ((c.get(Calendar.YEAR) & 0xFF00) >> 8));
                buffer.put((byte) (c.get(Calendar.YEAR) & 0x00FF));
                buffer.put((byte) ((c.get(Calendar.DAY_OF_WEEK) - 1) & 0xFF));
                return null;
            }

            @Override
            public Void visit(Data.UInt32 data) {
                buffer.putInt((int) data.get());
                return null;
            }

            @Override
            public Void visit(Data.UInt8 data) {
                buffer.put((byte) data.get());
                return null;
            }

            @Override
            public Void visit(Data.Bool data) {
                buffer.put(data.get() ? (byte) 1 : (byte) 0);
                return null;
            }
        });
    }
}
