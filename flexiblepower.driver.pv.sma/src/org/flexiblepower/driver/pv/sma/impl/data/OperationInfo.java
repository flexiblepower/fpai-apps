package org.flexiblepower.driver.pv.sma.impl.data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Map;

import org.flexiblepower.driver.pv.sma.impl.response.DataResponsePacket;
import org.flexiblepower.driver.pv.sma.impl.response.DataResponsePacket.Code;
import org.flexiblepower.driver.pv.sma.impl.response.DataResponsePacket.Element;

public class OperationInfo {

	private Date timestamp;
	private BigDecimal operationTime;
	private BigDecimal feedinTime;
	
	public OperationInfo(Map<Code, Element> elements) {
		timestamp = elements.get(DataResponsePacket.Code.OPERATION_TIME).getTimestamp();
		operationTime = elements.get(DataResponsePacket.Code.OPERATION_TIME).getValue().divide(new BigDecimal(3600), 3, RoundingMode.CEILING);
		feedinTime = elements.get(DataResponsePacket.Code.OPERATION_FEEDIN_TIME).getValue().divide(new BigDecimal(3600), 3, RoundingMode.CEILING);
	}
	
	public Date getTimestamp() {
		return timestamp;
	}
	
	public BigDecimal getOperationTime() {
		return operationTime;
	}

	public BigDecimal getFeedinTime() {
		return feedinTime;
	}

	@Override
	public String toString() {
		return "OperationInfo [timestamp=" + timestamp + ", operationTime=" + operationTime + ", feedinTime=" + feedinTime + "]";
	}
}
