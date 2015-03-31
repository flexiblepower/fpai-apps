package org.flexiblepower.driver.pv.sma.impl;

import org.flexiblepower.driver.pv.sma.impl.utils.ByteUtils;

public class L2Command {
	
	public static final L2Command LOGOFF = new L2Command(0xFFFD, 0x80, 0x0E, 0x01);
	public static final L2Command LOGON = new L2Command(0xFFFD, 0x80, 0x0C, 0x04);
	public static final L2Command PRODUCTION = new L2Command(0x5400, 0x80, 0x00, 0x02);
	public static final L2Command SPOT_AC_POWER = new L2Command(0x5100, 0x80, 0x00, 0x02);
	public static final L2Command SPOT_AC_FREQUENCY = new L2Command(0x5100, 0x80, 0x00, 0x02);
	public static final L2Command OPERATION_TIME = new L2Command(0x5400, 0x80, 0x00, 0x02);

	private final int command, commandGroup1, commandGroup2, commandGroup3;

	public L2Command(int command, int commandGroup1, int commandGroup2, int commandGroup3) {
		this.command = command & 0xffff;
		this.commandGroup1 = commandGroup1 & 0xff;
		this.commandGroup2 = commandGroup2 & 0xff;
		this.commandGroup3 = commandGroup3 & 0xff;
	}

	public int getCommand() {
		return command;
	}

	public int getCommandGroup1() {
		return commandGroup1;
	}

	public int getCommandGroup2() {
		return commandGroup2;
	}

	public int getCommandGroup3() {
		return commandGroup3;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		return prime * (prime * (prime * command + commandGroup1) + commandGroup2) + commandGroup3;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		} else if (obj == null || getClass() != obj.getClass()) {
			return false;
		} else {
			L2Command other = (L2Command) obj;
			return command == other.command && commandGroup1 == other.commandGroup1
					&& commandGroup2 == other.commandGroup2 && commandGroup3 == other.commandGroup3;
		}
	}

	@Override
	public String toString() {
		return ByteUtils.toHexString((short) command) + " " + ByteUtils.toHexString((byte) commandGroup1) + " "
				+ ByteUtils.toHexString((byte) commandGroup2) + " " + ByteUtils.toHexString((byte) commandGroup3);
	}
}
