package org.flexiblepower.driver.pv.sma.impl.data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

import org.flexiblepower.driver.pv.sma.impl.response.DataResponsePacket;
import org.flexiblepower.driver.pv.sma.impl.response.DataResponsePacket.Code;
import org.flexiblepower.driver.pv.sma.impl.response.DataResponsePacket.Element;

public class SpotAcInfo {

	private Date timestamp;
	private BigDecimal power;
	private BigDecimal frequency;

	public SpotAcInfo(Map<Code, Element> elements) {
		if (elements.containsKey(DataResponsePacket.Code.SPOT_AC_POWER)) {
			timestamp = elements.get(DataResponsePacket.Code.SPOT_AC_POWER).getTimestamp();
			power = elements.get(DataResponsePacket.Code.SPOT_AC_POWER).getValue();
		}
		if (elements.containsKey(DataResponsePacket.Code.SPOT_AC_FREQUENCY)) {
			frequency = elements.get(DataResponsePacket.Code.SPOT_AC_FREQUENCY).getValue();
		}
	}
	
	public Date getTimestamp() {
		return timestamp;
	}

	public BigDecimal getFrequency() {
		return frequency;
	}

	public BigDecimal getPower() {
		return power;
	}

	@Override
	public String toString() {
		return "SpotAcInfo [timestamp=" + timestamp + ", power=" + power + ", frequency=" + frequency + "]";
	}
}
