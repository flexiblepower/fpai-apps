package org.flexiblepower.driver.pv.sma.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.flexiblepower.driver.pv.sma.impl.utils.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class L2Codec {

    private final static Logger logger = LoggerFactory.getLogger(L2Codec.class);

    public static L2Packet readPacket(InputStream is) throws IOException {
        logger.trace("readPacket: begin");
        ByteBuffer buffer = ByteUtils.createBuffer();
        L2Packet result = null;

        do {
            buffer.clear();
            L1Packet l1Packet = L1Codec.readPacket(is);

            if (l1Packet.getCommand().equals(L1Command.L2PACKET_PART)) {
                logger.trace("Found L2Packet Part");
                ByteUtils.putBytesUnescaped(buffer, l1Packet.getData());

            } else if (l1Packet.getCommand().equals(L1Command.L2PACKET)) {
                logger.trace("Found L2Packet");
                ByteUtils.putBytesUnescaped(buffer, l1Packet.getData());
                buffer.flip();
                int calcCrc = ByteUtils.calcCrc(buffer);
                buffer.get(); // Already validated 0x7E at the start
                buffer.getInt(); // Header: 0xff036065
                buffer.get(); // Packet length / 4
                int destinationAddressHeader = buffer.get();
                String l2DestinationAddress = ByteUtils.readHexString(buffer, 6);
                buffer.get(); // Padding
                int sourceAddressHeader = buffer.get();
                String l2SourceAddress = ByteUtils.readHexString(buffer, 6);
                buffer.get(); // Padding
                int requestCode = buffer.get();
                buffer.get(); // Acknowledge
                int responseCode = buffer.get();
                int telegramNumber = buffer.get();
                buffer.get(); // Mystery 3
                int counter = buffer.get();
                int cmd1 = buffer.get();
                int cmd2 = buffer.get();
                int cmd3 = buffer.get();
                int command = buffer.getShort();
                byte[] l2data = new byte[buffer.remaining() - 3];
                buffer.get(l2data);
                int crc = buffer.getShort() & 0xffff;
                buffer.get(); // Closing 0x7E

                if (calcCrc == crc) {
                    result = new L2Packet(sourceAddressHeader,
                                          l1Packet.getSource(),
                                          l2SourceAddress,
                                          destinationAddressHeader,
                                          l1Packet.getDestination(),
                                          l2DestinationAddress,
                                          requestCode,
                                          responseCode,
                                          telegramNumber,
                                          counter,
                                          new L2Command(command, cmd1, cmd2, cmd3),
                                          l2data);
                } else {
                    logger.warn("Wrong CRC: calculated=" + calcCrc + ", packet=" + crc);
                }
            }
        } while (result == null);

        logger.debug("<-- " + ByteUtils.toHexString(buffer));
        logger.trace("readPacket: end");
        return result;
    }

    public static void writePacket(L2Packet l2Packet, OutputStream os) throws IOException {
        if (os == null) {
            throw new NullPointerException("No outputstream given");
        }

        logger.trace("writePacket: start");
        ByteBuffer buffer = ByteUtils.createBuffer();
        buffer.putInt(0x656003ff);
        buffer.put((byte) ((l2Packet.getData().length + 3) / 4 + 7));
        buffer.put((byte) l2Packet.getDestinationHeader());
        ByteUtils.writeHexString(buffer, l2Packet.getL2Destination());
        buffer.put((byte) 0); // Padding
        buffer.put((byte) l2Packet.getSourceHeader());
        ByteUtils.writeHexString(buffer, l2Packet.getL2Source());
        buffer.put((byte) 0); // Padding
        buffer.put((byte) l2Packet.getRequestCode());
        buffer.put((byte) 0); // Acknowledge
        buffer.put((byte) l2Packet.getResponseCode());
        buffer.put((byte) l2Packet.getTelegramNumber());
        buffer.put((byte) 0); // Mystery 3
        buffer.put((byte) l2Packet.getCounter());
        buffer.put((byte) l2Packet.getCommand().getCommandGroup1());
        buffer.put((byte) l2Packet.getCommand().getCommandGroup2());
        buffer.put((byte) l2Packet.getCommand().getCommandGroup3());
        buffer.putShort((short) l2Packet.getCommand().getCommand());
        buffer.put(l2Packet.getData());
        buffer.putShort((short) ByteUtils.calcCrc(buffer, 0, buffer.position()));

        buffer.flip();
        byte[] data = ByteUtils.escapeBytesAndDelim(buffer);
        L1Packet l1Packet = new L1Packet(l2Packet.getL1Source(), l2Packet.getL1Destination(), L1Command.L2PACKET, data);
        L1Codec.writePacket(l1Packet, os);

        logger.debug("--> " + ByteUtils.toHexString(buffer));
        logger.trace("writePacket: end");
    }
}
