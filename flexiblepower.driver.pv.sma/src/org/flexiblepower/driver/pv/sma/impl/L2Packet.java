package org.flexiblepower.driver.pv.sma.impl;

import java.util.concurrent.atomic.AtomicInteger;

import org.flexiblepower.driver.pv.sma.impl.utils.ByteUtils;

public class L2Packet {

    private static final AtomicInteger packetCounter = new AtomicInteger();

    private int sourceHeader;
	private String l1Source;
	private String l2Source;
	private int destinationHeader;
	private String l1Destination;
	private String l2Destination;
	private int requestCode;
	private int responseCode;
	private int telegramNumber;
	private int counter;
	private L2Command command;
	private byte[] data;
	
	public L2Packet(int sourceHeader, String l1Source, String l2Source, int destinationHeader, String l1Destination, String l2Destination, int requestCode, int responseCode, int telegramNumber, int counter, L2Command command, byte[] data) {
		this.sourceHeader = sourceHeader;
		this.l1Source = l1Source;
		this.l2Source = l2Source;
		this.destinationHeader = destinationHeader;
		this.l1Destination = l1Destination;
		this.l2Destination = l2Destination;
		this.requestCode = requestCode;
		this.responseCode = responseCode;
		this.telegramNumber = telegramNumber;
		if (counter >= 0) {
			this.counter = counter;
		} else {
			this.counter = packetCounter.incrementAndGet();
		}
		this.command = command;
		this.data = data;
	}

	public L2Packet(L2Packet base) {
	    sourceHeader = base.sourceHeader;
		l1Source = base.l1Source;
		l2Source = base.l2Source;
		destinationHeader = base.destinationHeader;
		l1Destination = base.l1Destination;
		l2Destination = base.l2Destination;
		requestCode = base.requestCode;
		responseCode = base.responseCode;
		telegramNumber = base.telegramNumber;
		counter = base.counter;
		command = base.command;
		data = base.data;
	}

	public int getSourceHeader() {
		return sourceHeader;
	}

	public String getL1Source() {
		return l1Source;
	}

	public String getL2Source() {
		return l2Source;
	}

	public int getDestinationHeader() {
		return destinationHeader;
	}

	public String getL1Destination() {
		return l1Destination;
	}

	public String getL2Destination() {
		return l2Destination;
	}

	public int getRequestCode() {
		return requestCode;
	}
	
	public int getResponseCode() {
		return responseCode;
	}

	public int getTelegramNumber() {
		return telegramNumber;
	}
	
	public int getCounter() {
		return counter;
	}
	
	public L2Command getCommand() {
		return command;
	}

	public byte[] getData() {
		return data;
	}

	@Override
	public String toString() {
		return "L2Packet [sourceHeader=" + sourceHeader + ", l1Source=" + l1Source + ", l2Source=" + l2Source
				+ ", destinationHeader=" + destinationHeader + ", l1Destination=" + l1Destination + ", l2Destination="
				+ l2Destination + ", requestCode=" + requestCode + ", responseCode=" + responseCode
				+ ", telegramNumber=" + telegramNumber + ", counter=" + counter + ", command=" + command + ", data="
				+ ByteUtils.toHexString(data) + "]";
	}
}
