package nl.tno.modbus.tcp.driver.test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.Arrays;

import org.flexiblepower.protocol.modbus.tcp.ModbusMessage;
import org.flexiblepower.protocol.modbus.tcp.ModbusMessage.Function;

import junit.framework.TestCase;

public class ModbusCodecTest extends TestCase {
    private final static char[] hex_chars = "0123456789abcdefg".toCharArray();

    private void assertEquals(byte[] expected, ByteBuffer result) {
        assertEquals(expected, Arrays.copyOf(result.array(), result.remaining()));
    }

    private void assertEquals(byte[] expected, byte[] result) {
        if (expected == null) {
            if (result != null) {
                throw new AssertionError("Expected null, but was not null");
            }
        } else if (result == null) {
            throw new AssertionError("Expected not null, but was null");
        } else if (expected.length != result.length) {
            throw new AssertionError("Expected length = " + expected.length + ", but got length = " + result.length);
        } else if (!Arrays.equals(expected, result)) {
            StringBuilder sb = new StringBuilder();
            sb.append("Byte arrays are not equals");
            sb.append("\nExpected:   ");
            for (int ix = 0; ix < expected.length; ix++) {
                int b = expected[ix];
                sb.append(hex_chars[(b >>> 4) & 0xf]);
                sb.append(hex_chars[b & 0xf]);
                if (ix % 4 == 3) {
                    sb.append(' ');
                }
            }
            sb.append("\nResult:     ");
            for (int ix = 0; ix < expected.length; ix++) {
                int b = result[ix];
                sb.append(hex_chars[(b >>> 4) & 0xf]);
                sb.append(hex_chars[b & 0xf]);
                if (ix % 4 == 3) {
                    sb.append(' ');
                }
            }
            sb.append("\nDifference: ");
            for (int ix = 0; ix < expected.length; ix++) {
                if (result[ix] == expected[ix]) {
                    sb.append("  ");
                } else {
                    sb.append("^^");
                }
                if (ix % 4 == 3) {
                    sb.append(' ');
                }
            }
            throw new AssertionError(sb.toString());
        }
    }

    private void requestTester(final int deviceId,
                               final int transactionId,
                               final Function function,
                               final int data,
                               final byte[] expected) throws IOException {
        // Test encoding
        ModbusMessage encoder = new ModbusMessage(deviceId, transactionId);
        encoder.startData(function);
        encoder.putWord(data);
        ByteBuffer result = encoder.finish();
        assertEquals(expected, result);

        // Test decoding
        ModbusMessage decoder = ModbusMessage.decode(Channels.newChannel(new ByteArrayInputStream(expected)));
        assertEquals(transactionId, decoder.getTransactionId());
        assertEquals(4, decoder.getLength());
        assertEquals(deviceId, decoder.getDeviceId());
        assertEquals(function, decoder.getFunction());
        assertEquals(data, decoder.getData().getShort());
    }

    public void testSingleCoilRequest() throws IOException {
        requestTester(3, 5, Function.READ_COIL, 8, new byte[] { 0, 5, 0, 0, 0, 4, 3, 1, 0, 8 });
        requestTester(3, 5, Function.READ_COIL, 0, new byte[] { 0, 5, 0, 0, 0, 4, 3, 1, 0, 0 });
    }

    public void testSingleHoldingRegisterRequest() throws IOException {
        requestTester(251, 256, Function.READ_HOLDING_REGISTER, 257, new byte[] { 1, 0, 0, 0, 0, 4, -5, 3, 1, 1 });
    }
}
