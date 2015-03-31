package org.flexiblepower.driver.pv.sma.impl.request;

import java.nio.ByteBuffer;

import org.flexiblepower.driver.pv.sma.impl.L2Command;
import org.flexiblepower.driver.pv.sma.impl.L2Packet;
import org.flexiblepower.driver.pv.sma.impl.utils.ByteUtils;

public class LogOnPacket extends L2Packet {
	
    public static LogOnPacket create(String sourceAddress, String password) {
    	ByteBuffer buffer = ByteUtils.createBuffer();

    	int now = (int) (System.currentTimeMillis() / 1000);

        buffer.putInt(0x07); // User
        buffer.putInt(0x00000384); // Timeout = 900sec ?
        buffer.putInt(now);
        buffer.putInt(0);
        byte[] passwordBytes = password.getBytes();
        byte[] encodedPassword = new byte[12];
        for (int ix = 0; ix < encodedPassword.length; ix++) {
            if (ix < passwordBytes.length) {
                encodedPassword[ix] = (byte) (0x88 ^ passwordBytes[ix]);
            } else {
                encodedPassword[ix] = (byte) 0x88;
            }
        }
        buffer.put(encodedPassword);
        buffer.flip();

        return new LogOnPacket(sourceAddress, now, password, ByteUtils.toByteArray(buffer));
    }

    private final int now;
    private final String password;

    private LogOnPacket(String sourceAddress, int now, String password, byte[] data) {
        super(1, ByteUtils.BROADCAST_ADDRESS, sourceAddress, 0xa0, ByteUtils.UNKNOWN_ADDRESS, ByteUtils.UNKNOWN_ADDRESS, 1, 0, 0, 0, L2Command.LOGON, data);
        this.now = now;
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public int getNow() {
        return now;
    }
}
