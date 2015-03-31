package org.flexiblepower.driver.pv.sma.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.flexiblepower.driver.pv.sma.impl.utils.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class L1Codec {
	
	private final static Logger logger = LoggerFactory.getLogger(L1Codec.class);

	private final static int HEADER_LENGTH = 18;

    private static int calcHeaderCrc(int packetLength) {
        return (ByteUtils.DELIMETER & 0xff) ^ (packetLength & 0xff) ^ ((packetLength >> 8) & 0xff);
    }
	
	public static L1Packet readPacket(InputStream is) throws IOException {
		logger.trace("readPacket: begin");
	    ByteBuffer buffer = ByteUtils.createBuffer();
        int packetLength = 0;
        int crc = 0, calcCrc = 1;

        do {
            buffer.clear();
            ByteUtils.readFully(is, buffer, HEADER_LENGTH);

            if (buffer.get() == ByteUtils.DELIMETER) {
                packetLength = buffer.getShort();
                crc = buffer.get();
                calcCrc = calcHeaderCrc(packetLength);
            }
        } while (crc != calcCrc);

        String sourceAddress = ByteUtils.readHexString(buffer, 6);
        String destinationAddress = ByteUtils.readHexString(buffer, 6);

        L1Command command = new L1Command(buffer.getShort());

        packetLength -= HEADER_LENGTH;
        buffer.clear();
        ByteUtils.readFully(is, buffer, packetLength);
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);

        logger.debug("<-- " + ByteUtils.toHexString(buffer));
        logger.trace("readPacket: end");
        return new L1Packet(sourceAddress, destinationAddress, command, data);
	}
	   
	public static void writePacket(L1Packet l1Packet, OutputStream os) throws IOException {
		logger.trace("writePacket: start");
		ByteBuffer buffer = ByteUtils.createBuffer();

		buffer.put(ByteUtils.DELIMETER);
        int packetLength = 18 + l1Packet.getData().length;
        buffer.putShort((short) packetLength);
        buffer.put((byte) calcHeaderCrc(packetLength));
        
        ByteUtils.writeHexString(buffer, l1Packet.getSource());
        ByteUtils.writeHexString(buffer, l1Packet.getDestination());
        
        buffer.putShort(l1Packet.getCommand().getCode());
        buffer.put(l1Packet.getData());
        
        buffer.flip();
        ByteUtils.writeFully(buffer, os);

        logger.debug("--> " + ByteUtils.toHexString(buffer));
        logger.trace("writePacket: end");
	}
}
