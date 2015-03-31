package org.flexiblepower.driver.pv.sma.impl;

import org.flexiblepower.driver.pv.sma.impl.utils.ByteUtils;

public class L1Packet {

	private String source;
	private String destination;
	private L1Command command;
	private byte[] data;

	public L1Packet(String source, String destination, L1Command command, byte[] data) {
		this.source = source;
		this.destination = destination;
		this.command = command;
		this.data = data;
	}

	public String getSource() {
		return source;
	}

	public String getDestination() {
		return destination;
	}

	public L1Command getCommand() {
		return command;
	}

	public byte[] getData() {
		return data;
	}

	@Override
	public String toString() {
		return "L1Packet [source=" + source + ", destination=" + destination + ", command=" + command + ", data="
				+ ByteUtils.toHexString(data) + "]";
	}
}
