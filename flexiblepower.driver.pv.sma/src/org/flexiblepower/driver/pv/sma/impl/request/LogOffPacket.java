package org.flexiblepower.driver.pv.sma.impl.request;

import java.nio.ByteBuffer;

import org.flexiblepower.driver.pv.sma.impl.L2Command;
import org.flexiblepower.driver.pv.sma.impl.L2Packet;
import org.flexiblepower.driver.pv.sma.impl.utils.ByteUtils;

public class LogOffPacket extends L2Packet {
	
    public static LogOffPacket create(String sourceAddress) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(0xFFFFFFFF);
        buffer.flip();
        
        return new LogOffPacket(sourceAddress, ByteUtils.toByteArray(buffer));
    }

    private LogOffPacket(String sourceAddress, byte[] data) {
        super(3, ByteUtils.BROADCAST_ADDRESS, sourceAddress, 0xa0, ByteUtils.UNKNOWN_ADDRESS, ByteUtils.UNKNOWN_ADDRESS, 3, 0, 0, 0, L2Command.LOGOFF, data);
    }
}
