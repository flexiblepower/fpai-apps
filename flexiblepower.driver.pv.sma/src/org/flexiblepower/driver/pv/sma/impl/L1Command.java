package org.flexiblepower.driver.pv.sma.impl;

public class L1Command {
	
    public static final L1Command L2PACKET = new L1Command(0x1);
	public static final L1Command L2PACKET_PART = new L1Command(0x8);

	public static final L1Command HANDSHAKE_1 = new L1Command(0x2);
	public static final L1Command HANDSHAKE_2 = new L1Command(0x2);
	public static final L1Command HANDSHAKE_3 = new L1Command(0xa);
	public static final L1Command HANDSHAKE_4 = new L1Command(0xc);
	public static final L1Command HANDSHAKE_5 = new L1Command(0x5);
	
	public static final L1Command REQUEST = new L1Command(0x3);
	public static final L1Command RESPONSE = new L1Command(0x4);
	
	public static final L1Command ERROR = new L1Command(0x7);

    private final short code;

    public L1Command(int code) {
    	this.code = (short) (code & 0xffff);
    }
    
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		L1Command other = (L1Command) obj;
		if (code != other.code)
			return false;
		return true;
	}

	public short getCode() {
        return code;
    }
	
	@Override
	public String toString() {
		return "L1Command [code=" + code + "]";
	}
}