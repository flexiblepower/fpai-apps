package org.flexiblepower.protocol.modbus.tcp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ModbusMessage} represents a single message in the modbus protocol. It wraps a {@link ByteBuffer} that
 * contains the raw data that will be send or has been received over the connection.
 */
public class ModbusMessage {
    private static final Logger logger = LoggerFactory.getLogger(ModbusMessage.class);

    private static final int TRANSACTION_START = 0;
    private static final int LENGTH_START = TRANSACTION_START + 4;
    private static final int DEVICEID_START = LENGTH_START + 2;
    private static final int HEADER_LENGTH = DEVICEID_START + 1;
    private static final int FUNCTION_START = HEADER_LENGTH;
    private static final int DATA_START = HEADER_LENGTH + 1;

    /**
     * A list of all the functions that are known in the modbus protocol.
     */
    public static enum Function {
        READ_COIL(0x01),
        READ_DISCRETE_INPUT(0x02),
        READ_HOLDING_REGISTER(0x03),
        READ_INPUT_REGISTER(0x04),
        WRITE_SINGLE_COIL(0x05),
        WRITE_SINGLE_REGISTER(0x06),
        WRITE_MULTIPLE_COILS(0x0F),
        WRITE_MULTIPLE_REGISTERS(0x10),

        ERROR_READ_COIL(0x81),
        ERROR_READ_DISCRETE_INPUT(0x82),
        ERROR_READ_HOLDING_REGISTER(0x83),
        ERROR_READ_INPUT_REGISTER(0x84),
        ERROR_WRITE_SINGLE_COIL(0x85),
        ERROR_WRITE_SINGLE_REGISTER(0x86),
        ERROR_WRITE_MULTIPLE_COILS(0x8F),
        ERROR_WRITE_MULTIPLE_REGISTERS(0x90),

        ERROR_UNKNOWN_FUNCTION(0xFF);

        private final byte code;

        private Function(int code) {
            this.code = (byte) code;
        }

        /**
         * @return The full code
         */
        public byte getCode() {
            return code;
        }

        /**
         * @return The function part of the code
         */
        public byte getFunctionCode() {
            return (byte) (code & 0x7f);
        }

        /**
         * @return true when it is a error in response to a function call
         */
        public boolean isError() {
            return (code & 0x80) != 0;
        }

        @Override
        public String toString() {
            return super.toString() + " (" + Integer.toHexString(getCode()) + ")";
        }
    }

    /**
     * A {@link Map} that contains all the functions for each byte, for easy calling.
     */
    public static final Map<Byte, Function> FUNCTIONS;

    static {
        Map<Byte, Function> fs = new HashMap<Byte, ModbusMessage.Function>();
        for (Function f : Function.values()) {
            fs.put(f.getCode(), f);
        }
        FUNCTIONS = Collections.unmodifiableMap(fs);
    }

    private static final ByteBuffer HEADER_BUFFER = ByteBuffer.allocate(HEADER_LENGTH);

    /**
     * Reads a {@link ModbusMessage} from a channel.
     *
     * @param readChannel
     *            the channel that will be used to read the raw bytes from.
     * @return The {@link ModbusMessage} that has been read. This message is already complete and you should only use
     *         the get methods on this message.
     * @throws ModbusException
     *             when an error occurred while decoding the message
     * @throws IOException
     *             when an I/O error occurred while reading from the channel
     */
    public static ModbusMessage decode(ReadableByteChannel readChannel) throws IOException {
        ByteBuffer buffer = null;

        synchronized (HEADER_BUFFER) {
            HEADER_BUFFER.order(ByteOrder.BIG_ENDIAN);
            HEADER_BUFFER.rewind();
            int read = readChannel.read(HEADER_BUFFER);
            if (read != HEADER_LENGTH) {
                throw new ModbusException("Could not read from the input. Is the channel closed?");
            }

            int dataLength = HEADER_BUFFER.getShort(LENGTH_START);
            buffer = ByteBuffer.allocate(DEVICEID_START + dataLength);
            buffer.order(ByteOrder.BIG_ENDIAN);
            HEADER_BUFFER.flip();
            buffer.put(HEADER_BUFFER);
        }

        readChannel.read(buffer);
        if (buffer.hasRemaining()) {
            throw new ModbusException("Not enough bytes read");
        }
        return new ModbusMessage(buffer);
    }

    private final ByteBuffer buffer;

    private ModbusMessage(ByteBuffer buffer) {
        this.buffer = buffer;
        logger.trace("Decoded " + this);
    }

    /**
     * Starts a new {@link ModbusMessage} that is not yet complete (you should call {@link #startData(Function)} and
     * {@link #putWord(int)} methods and finally {@link #finish()}).
     *
     * @param device
     *            The device for which the message is being created. Will use its deviceId.
     * @param transactionId
     *            The transaction number that is used in the protocol.
     */
    public ModbusMessage(Device device, int transactionId) {
        this(device.getDeviceId(), transactionId);
    }

    /**
     * Starts a new {@link ModbusMessage} that is not yet complete (you should call {@link #startData(Function)} and
     * {@link #putWord(int)} methods and finally {@link #finish()}).
     *
     * @param deviceId
     *            The device identifier that is used in the protocol.
     * @param transactionId
     *            The transaction number that is used in the protocol.
     */
    public ModbusMessage(int deviceId, int transactionId) {
        buffer = ByteBuffer.allocate(1500);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) transactionId);
        buffer.putInt(0); // Length will be filled in later
        buffer.put((byte) deviceId);
    }

    /**
     * Puts the function code in the buffer. After this has been called, you can put bits or words in it for the rest of
     * the data.
     *
     * @param function
     *            The function that you want to add.
     * @return This {@link ModbusMessage}
     */
    public ModbusMessage startData(Function function) {
        buffer.put(function.code);
        return this;
    }

    /**
     * Puts a set of bits in the buffer.
     *
     * @param vector
     *            The bits.
     * @return This {@link ModbusMessage}
     */
    public ModbusMessage putBits(BitVector vector) {
        if (buffer.position() < DATA_START) {
            throw new IllegalStateException("Always call startData before putting in data");
        }
        vector.writeTo(buffer);
        return this;
    }

    /**
     * Puts a single word in the buffer. Only 16 bits will be written.
     *
     * @param word
     *            The input word.
     * @return This {@link ModbusMessage}
     */
    public ModbusMessage putWord(int word) {
        if (buffer.position() < DATA_START) {
            throw new IllegalStateException("Always call startData before putting in data");
        }
        buffer.putShort((short) word);
        return this;
    }

    /**
     * Finishes the message by setting the length part of the header.
     *
     * @return The {@link ByteBuffer} that can be used for writing the message.
     */
    public ByteBuffer finish() {
        int length = buffer.position() - DEVICEID_START;
        buffer.putShort(LENGTH_START, (short) length);
        buffer.flip();

        logger.trace("Created " + this);
        return buffer;
    }

    /**
     * @return The transaction identifier.
     */
    public int getTransactionId() {
        return buffer.getShort(TRANSACTION_START) & 0xffff;
    }

    /**
     * @return The length of the message.
     */
    public int getLength() {
        return buffer.getShort(LENGTH_START) & 0xffff;
    }

    /**
     * @return The device identifier of the message
     */
    public int getDeviceId() {
        return buffer.get(DEVICEID_START) & 0xff;
    }

    /**
     * @return The function of the message
     */
    public Function getFunction() {
        Function function = FUNCTIONS.get(buffer.get(FUNCTION_START));
        return function == null ? Function.ERROR_UNKNOWN_FUNCTION : function;
    }

    /**
     * @return The ByteBuffer that provides the raw data that is in the message.
     */
    public ByteBuffer getData() {
        buffer.limit(getLength() + DEVICEID_START);
        buffer.position(DATA_START);
        return buffer;
    }

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Transaction ").append(getTransactionId());
        sb.append(" DeviceId ").append(getDeviceId());
        sb.append(" Function ").append(getFunction());
        sb.append(" Data: ");
        byte[] bs = buffer.array();
        for (int ix = DATA_START; ix < getLength() + DEVICEID_START; ix++) {
            int b = bs[ix];
            sb.append(HEX_CHARS[(b >>> 4) & 0xf]);
            sb.append(HEX_CHARS[b & 0xf]);
            if (ix % 2 == 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
