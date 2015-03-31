package org.flexiblepower.driver.pv.sma.impl.utils;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteUtils {
	
    public static final String BROADCAST_ADDRESS = "000000000000";
    public static final String UNKNOWN_ADDRESS = "FFFFFFFFFFFF";

    public static final byte DELIMETER = 0x7E;
    public static final byte ESCAPE_CHARACTER = 0x7D;

    private static final char[] HEX = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    public static int calcCrc(ByteBuffer buffer) {
        if (buffer.get(buffer.position()) != DELIMETER) {
            throw new IllegalStateException("Not at the start of a packet");
        }
        int skip = 0; // By default skip no bytes
        if (buffer.get(buffer.limit() - 1) == DELIMETER) {
            // Full packet, skip last 3 bytes
            skip = 3;
        }
        return calcCrc(buffer, buffer.position() + 1, buffer.limit() - skip);
    }

    public static int calcCrc(ByteBuffer buffer, int fromIx, int untilIx) {
        FcsCheckSum checksum = new FcsCheckSum();
        for (int ix = fromIx; ix < untilIx; ix++) {
            checksum.addByte(buffer.get(ix));
        }
        return checksum.getChecksum();
    }

    public static ByteBuffer createBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(520);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer;
    }

    public static byte[] escapeBytesAndDelim(ByteBuffer l2buffer) {
        int length = l2buffer.remaining() + 2;
        for (int ix = l2buffer.position(); ix < l2buffer.limit(); ix++) {
            if (shouldEscape(l2buffer.get(ix))) {
                length++;
            }
        }
        byte[] result = new byte[length];
        int ix = 0;
        result[ix++] = DELIMETER; // First delimiter
        while (l2buffer.remaining() > 0) {
            byte b = l2buffer.get();
            if (shouldEscape(b)) {
                result[ix++] = ESCAPE_CHARACTER;
                result[ix++] = (byte) (b ^ 0x20);
            } else {
                result[ix++] = b;
            }
        }
        result[ix++] = DELIMETER; // Last delimiter
        return result;
    }

    public static byte[] hexStringToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    public static void putBytesUnescaped(ByteBuffer buffer, byte[] data) {
        if (data.length > buffer.remaining()) {
            throw new IllegalArgumentException("Not enough room in the buffer for the data");
        }
        for (int ix = 0; ix < data.length; ix++) {
            byte b = data[ix];
            if (b == ESCAPE_CHARACTER) {
                buffer.put((byte) (data[++ix] ^ 0x20));
            } else {
                buffer.put(b);
            }
        }
    }

    /**
     * Reads the given nr of bytes from the InputStream and writes them to the buffer. When the nr of bytes is zero or
     * less, this method will not do anything.
     * 
     * @param is
     *            The {@link InputStream} that will be read.
     * @param buffer
     *            The {@link ByteBuffer} that the bytes will be written to.
     * @param nrBytes
     *            The nr of bytes that will be read.
     * @throws IOException
     *             If something goes wrong while reading the {@link InputStream}.
     * @throws EOFException
     *             When the {@link InputStream} does not have enough bytes left to be read.
     * @throws IllegalArgumentException
     *             When the room remaining in the buffer is less that the nr of bytes given.
     */
    public static void readFully(InputStream is, ByteBuffer buffer, int nrBytes) throws IOException {
        if (buffer.remaining() < nrBytes) {
            throw new IllegalArgumentException("There is not enough room left in the buffer to write " + nrBytes
                                               + " bytes, there is only room for "
                                               + buffer.remaining()
                                               + " bytes");
        }
        for (int i = 0; i < nrBytes; i++) {
            int b = is.read();
            if (b < 0) {
                throw new EOFException();
            }
            buffer.put((byte) (b & 0xff));
        }
        buffer.flip();
    }

    public static String readHexString(ByteBuffer buffer, int nrBytes) {
        byte[] bytes = new byte[nrBytes];
        for (int i = bytes.length - 1; i >= 0; i--) {
            bytes[i] = buffer.get();
        }
        return toHexString(bytes);
    }

    public static boolean shouldEscape(byte b) {
        return (b == DELIMETER || b == ESCAPE_CHARACTER || b == 0x11 || b == 0x12 || b == 0x13);
    }
    
    public static byte[] toByteArray(ByteBuffer buffer) {
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        return data;
    }

    public static String toHexString(byte value) {
        return new String(new char[] { HEX[(value >>> 4) & 0xf], HEX[(value >>> 0) & 0xf] });
    }

    public static String toHexString(byte[] bytes) {
        return toHexString(bytes, 0, bytes.length, 0);
    }

    public static String toHexString(byte[] bytes, int offset, int length, int spacing) {
        StringBuilder sb = new StringBuilder();
        for (int ix = offset; ix < offset + length; ix++) {
            sb.append(HEX[(bytes[ix] >>> 4) & 0xf]);
            sb.append(HEX[bytes[ix] & 0xf]);
            if (spacing > 0 && ix % spacing == spacing - 1 && ix < offset + length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    public static String toHexString(ByteBuffer byteBuffer) {
    	byteBuffer.rewind();
        return toHexString(byteBuffer.array(),
                           byteBuffer.arrayOffset() + byteBuffer.position(),
                           byteBuffer.remaining(),
                           4);
    }

    public static String toHexString(int value) {
        return new String(new char[] { HEX[(value >>> 28) & 0xf],
                                      HEX[(value >>> 24) & 0xf],
                                      HEX[(value >>> 20) & 0xf],
                                      HEX[(value >>> 16) & 0xf],
                                      HEX[(value >>> 12) & 0xf],
                                      HEX[(value >>> 8) & 0xf],
                                      HEX[(value >>> 4) & 0xf],
                                      HEX[(value >>> 0) & 0xf] });
    }

    public static String toHexString(short value) {
        return new String(new char[] { HEX[(value >>> 12) & 0xf],
                                      HEX[(value >>> 8) & 0xf],
                                      HEX[(value >>> 4) & 0xf],
                                      HEX[(value >>> 0) & 0xf] });
    }

    /**
     * Writes all the remaining bytes of the {@link ByteBuffer} to the given {@link OutputStream}. When you have written
     * to the buffer, don't forget to call {@link ByteBuffer#flip()} to make those readable again.
     * 
     * @param buffer
     *            The {@link ByteBuffer} that will be read completely.
     * @param os
     *            The {@link OutputStream} that will be written to.
     * @throws IOException
     *             If any error occurs while writing to the {@link OutputStream}.
     * @throws IllegalArgumentException
     *             When the {@link ByteBuffer} is not backed by an array.
     */
    public static void writeFully(ByteBuffer buffer, OutputStream os) throws IOException {
        if (!buffer.hasArray()) {
            throw new IllegalArgumentException("This only works with buffer that have a backing array");
        }
        os.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
        buffer.position(buffer.limit());
    }

    public static void writeHexString(ByteBuffer buffer, String hex) {
        byte[] bytes = hexStringToBytes(hex);
        for (int i = bytes.length - 1; i >= 0; i--) {
            buffer.put(bytes[i]);
        }
    }

    private ByteUtils() {
        // No instance of ByteUtils, making it a static class
    }
}
