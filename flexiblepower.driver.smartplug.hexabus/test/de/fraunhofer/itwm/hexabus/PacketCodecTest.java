package de.fraunhofer.itwm.hexabus;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import junit.framework.TestCase;
import nl.tno.hexabus.protocol.Channel;
import nl.tno.hexabus.protocol.Data;
import nl.tno.hexabus.protocol.Packet;
import nl.tno.hexabus.protocol.PacketCodec;

public class PacketCodecTest extends TestCase {
    private static byte parse(char c) {
        if (c >= '0' && c <= '9') {
            return (byte) (c - '0');
        } else if (c >= 'a' && c <= 'f') {
            return (byte) (c - 'a' + 10);
        } else if (c >= 'A' && c <= 'F') {
            return (byte) (c - 'A' + 10);
        } else {
            throw new IllegalArgumentException();
        }
    }

    private static byte[] parse(String hex) {
        char[] chars = hex.toCharArray();
        byte[] result = new byte[chars.length / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (parse(chars[2 * i]) * 16 + parse(chars[2 * i + 1]));
        }
        return result;
    }

    private final PacketCodec codec;
    private final Inet6Address address;
    private final int port;

    public PacketCodecTest() throws UnknownHostException {
        codec = new PacketCodec();
        address = (Inet6Address) InetAddress.getByName("fe80::1");
        port = 61616;
    }

    public void performTest(String hexCode, Packet expectedPacket) {
        // Part 1: decoding the hexCode
        assertEquals(expectedPacket, codec.read(address, port, ByteBuffer.wrap(parse(hexCode))));

        // Part 2: encoding the packet
        ByteBuffer buffer = ByteBuffer.allocate(Channel.MAX_PACKET_SIZE);
        codec.write(expectedPacket, buffer);
        assertEquals(hexCode, new PacketCodec.HexDumper(buffer).toString());
    }

    public void testEndpointQuery() {
        performTest("485830420a0000f0b9", new Packet.EndpointQuery(address, port, 0));
    }

    public void testInfo() {
        performTest("485830420100020300000000ca3d", new Packet.Info(address, port, new Data.UInt32(2, 0)));
    }

    public void testEndpointInfo() {
        performTest("48583042090000034865786162757320536f636b6574202d20446576656c6f706d656e742056657273696f6e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000713c",
                    new Packet.EndpointInfo(address,
                                            port,
                                            Data.UInt32.class,
                                            new Data.Text(0, "Hexabus Socket - Development Version")));
        performTest("485830420900200000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000050",
                    new Packet.EndpointInfo(address, port, Data.Unknown.class, new Data.Text(32, "")));
    }
}
