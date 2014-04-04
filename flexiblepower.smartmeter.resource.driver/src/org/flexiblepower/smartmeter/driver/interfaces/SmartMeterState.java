package org.flexiblepower.smartmeter.driver.interfaces;

import java.math.BigDecimal;
import java.util.Date;

import org.flexiblepower.ral.ResourceState;

public interface SmartMeterState extends ResourceState {

    public SmartMeterMeasurement getMeasurement();

    public Date getTimeStamp();

    public BigDecimal getCurrentPowerConsumptionW();

    public BigDecimal getCurrentPowerProductionW();

    public BigDecimal getElectricityConsumptionLowRateKwh();

    public BigDecimal getElectricityConsumptionNormalRateKwh();

    public BigDecimal getElectricityProductionLowRateKwh();

    public BigDecimal getElectricityProductionNormalRateKwh();

    public BigDecimal getGasConsumptionM3();

}
