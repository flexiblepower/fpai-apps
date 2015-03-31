package org.flexiblepower.driver.pv.sma.impl.data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

import org.flexiblepower.driver.pv.sma.impl.response.DataResponsePacket;
import org.flexiblepower.driver.pv.sma.impl.response.DataResponsePacket.Code;
import org.flexiblepower.driver.pv.sma.impl.response.DataResponsePacket.Element;

public class ProductionInfo {
	
	private Date timestamp;
	private BigDecimal lifetime;
	private BigDecimal today;
	
	public ProductionInfo(Map<Code, Element> elements) {
		timestamp = elements.get(DataResponsePacket.Code.PROD_LIFETIME).getTimestamp();
		lifetime = elements.get(DataResponsePacket.Code.PROD_LIFETIME).getValue();
		today = elements.get(DataResponsePacket.Code.PROD_TODAY).getValue();
	}
	
	public Date getTimestamp() {
		return timestamp;
	}
	
	public BigDecimal getLifetime() {
		return lifetime;
	}
	
	public BigDecimal getToday() {
		return today;
	}

	@Override
	public String toString() {
		return "ProductionInfo [timestamp=" + timestamp + ", lifetime=" + lifetime + ", today=" + today + "]";
	}
}
