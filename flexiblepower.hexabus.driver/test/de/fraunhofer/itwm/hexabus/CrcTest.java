package de.fraunhofer.itwm.hexabus;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import junit.framework.TestCase;

public class CrcTest extends TestCase {
    public void testParsing() throws UnknownHostException {
        byte[] input = new byte[] { 72, 88, 48, 66, 1, 0, 2, 3, 0, 0, 0, 0, -54, 61 };
        DatagramPacket p = new DatagramPacket(input, input.length);
        p.setAddress(InetAddress.getByName("localhost"));
        p.setPort(61616);
        HexabusPacket packet = Hexabus.parsePacket(p);
        System.out.println(packet);
    }
}
