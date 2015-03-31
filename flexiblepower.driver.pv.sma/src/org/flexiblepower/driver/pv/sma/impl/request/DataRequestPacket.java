package org.flexiblepower.driver.pv.sma.impl.request;

import java.nio.ByteBuffer;

import org.flexiblepower.driver.pv.sma.impl.L2Command;
import org.flexiblepower.driver.pv.sma.impl.L2Packet;
import org.flexiblepower.driver.pv.sma.impl.utils.ByteUtils;

public class DataRequestPacket extends L2Packet {
	
    public enum Type {
        PRODUCTION(L2Command.PRODUCTION, 0x00260100, 0x002622FF),
        SPOT_AC_POWER(L2Command.SPOT_AC_POWER, 0x00263F00, 0x00263FFF),
        SPOT_AC_FREQUENCY(L2Command.SPOT_AC_FREQUENCY, 0x00465700, 0x004657FF),
        OPERATION_TIME(L2Command.OPERATION_TIME, 0x00462E00, 0x00462FFF);

        private final L2Command command;
        private final int startRange;
        private final int endRange;

        Type(L2Command command, int startRange, int endRange) {
            this.command = command;
            this.startRange = startRange;
            this.endRange = endRange;
        }

        public L2Command getCommand() {
            return command;
        }

        public int getEndRange() {
            return endRange;
        }

        public int getStartRange() {
            return startRange;
        }
    }

    public static DataRequestPacket create(String clientAddress, String inverterAddress, Type type) {
        ByteBuffer buffer = ByteUtils.createBuffer();
        buffer.putInt(type.getStartRange());
        buffer.putInt(type.getEndRange());
        buffer.flip();

        return new DataRequestPacket(clientAddress, inverterAddress, type, ByteUtils.toByteArray(buffer));
    }

    private DataRequestPacket(String clientAddress, String inverterAddress, Type type, byte[] data) {
        super(0, ByteUtils.BROADCAST_ADDRESS, clientAddress, 0xa0, inverterAddress, ByteUtils.UNKNOWN_ADDRESS, 0, 0, 0, 0, type.getCommand(), data);
    }
}
