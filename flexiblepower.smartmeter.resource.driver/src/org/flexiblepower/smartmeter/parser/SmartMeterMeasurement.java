package org.flexiblepower.smartmeter.parser;

import java.math.BigDecimal;
import java.util.Date;

import org.flexiblepower.smartmeter.driver.interfaces.SmartMeterState;

public class SmartMeterMeasurement implements SmartMeterState {
    private Date timestamp;
    private BigDecimal electricityConsumptionLowRateKwh;
    private BigDecimal electricityConsumptionNormalRateKwh;
    private BigDecimal electricityProductionLowRateKwh;
    private BigDecimal electricityProductionNormalRateKwh;
    private BigDecimal currentPowerConsumptionW;
    private BigDecimal currentPowerProductionW;
    private BigDecimal gasConsumptionM3;

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public BigDecimal getElectricityConsumptionLowRateKwh() {
        return electricityConsumptionLowRateKwh;
    }

    public void setElectricityConsumptionLowRateKwh(BigDecimal electricityConsumptionLowRateKwh) {
        this.electricityConsumptionLowRateKwh = electricityConsumptionLowRateKwh;
    }

    @Override
    public BigDecimal getElectricityConsumptionNormalRateKwh() {
        return electricityConsumptionNormalRateKwh;
    }

    public void setElectricityConsumptionNormalRateKwh(BigDecimal electricityConsumptionNormalRateKwh) {
        this.electricityConsumptionNormalRateKwh = electricityConsumptionNormalRateKwh;
    }

    @Override
    public BigDecimal getElectricityProductionLowRateKwh() {
        return electricityProductionLowRateKwh;
    }

    public void setElectricityProductionLowRateKwh(BigDecimal electricityProductionLowRateKwh) {
        this.electricityProductionLowRateKwh = electricityProductionLowRateKwh;
    }

    @Override
    public BigDecimal getElectricityProductionNormalRateKwh() {
        return electricityProductionNormalRateKwh;
    }

    public void setElectricityProductionNormalRateKwh(BigDecimal electricityProductionNormalRateKwh) {
        this.electricityProductionNormalRateKwh = electricityProductionNormalRateKwh;
    }

    @Override
    public BigDecimal getCurrentPowerConsumptionW() {
        return currentPowerConsumptionW;
    }

    public void setCurrentPowerConsumptionW(BigDecimal currentPowerConsumptionW) {
        this.currentPowerConsumptionW = currentPowerConsumptionW;
    }

    @Override
    public BigDecimal getCurrentPowerProductionW() {
        return currentPowerProductionW;
    }

    public void setCurrentPowerProductionW(BigDecimal currentPowerProductionW) {
        this.currentPowerProductionW = currentPowerProductionW;
    }

    @Override
    public BigDecimal getGasConsumptionM3() {
        return gasConsumptionM3;
    }

    public void setGasConsumptionM3(BigDecimal gasConsumptionM3) {
        this.gasConsumptionM3 = gasConsumptionM3;
    }

    @Override
    public String toString() {
        return ("Time Of Measurement: " + timestamp + "\n" +
                "ElectricityConsumptionLowRateKwh: " + electricityConsumptionLowRateKwh + "\n" +
                "ElectricityConsumptionNormalRateKwh: " + electricityConsumptionNormalRateKwh + "\n" +
                "ElectricityProductionLowRateKwh: " + electricityProductionLowRateKwh + "\n" +
                "ElectricityProductionNormalRateKwh: " + electricityProductionNormalRateKwh + "\n" +
                "CurrentPowerConsumptionW: " + currentPowerConsumptionW + "\n" +
                "CurrentPowerProductionW: " + currentPowerProductionW + "\n" +
                "GasConsumptionM3: " + gasConsumptionM3 + "\n");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SmartMeterMeasurement that = (SmartMeterMeasurement) o;

        if (currentPowerConsumptionW != null ? !currentPowerConsumptionW.equals(that.currentPowerConsumptionW)
                                            : that.currentPowerConsumptionW != null) {
            return false;
        }
        if (currentPowerProductionW != null ? !currentPowerProductionW.equals(that.currentPowerProductionW)
                                           : that.currentPowerProductionW != null) {
            return false;
        }
        if (electricityConsumptionLowRateKwh != null ? !electricityConsumptionLowRateKwh.equals(that.electricityConsumptionLowRateKwh)
                                                    : that.electricityConsumptionLowRateKwh != null) {
            return false;
        }
        if (electricityConsumptionNormalRateKwh != null ? !electricityConsumptionNormalRateKwh.equals(that.electricityConsumptionNormalRateKwh)
                                                       : that.electricityConsumptionNormalRateKwh != null) {
            return false;
        }
        if (electricityProductionLowRateKwh != null ? !electricityProductionLowRateKwh.equals(that.electricityProductionLowRateKwh)
                                                   : that.electricityProductionLowRateKwh != null) {
            return false;
        }
        if (electricityProductionNormalRateKwh != null ? !electricityProductionNormalRateKwh.equals(that.electricityProductionNormalRateKwh)
                                                      : that.electricityProductionNormalRateKwh != null) {
            return false;
        }
        if (gasConsumptionM3 != null ? !gasConsumptionM3.equals(that.gasConsumptionM3) : that.gasConsumptionM3 != null) {
            return false;
        }
        if (timestamp != null ? !timestamp.equals(that.timestamp) : that.timestamp != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = timestamp != null ? timestamp.hashCode() : 0;
        result = 31 * result
                 + (electricityConsumptionLowRateKwh != null ? electricityConsumptionLowRateKwh.hashCode() : 0);
        result = 31 * result
                 + (electricityConsumptionNormalRateKwh != null ? electricityConsumptionNormalRateKwh.hashCode() : 0);
        result = 31 * result
                 + (electricityProductionLowRateKwh != null ? electricityProductionLowRateKwh.hashCode() : 0);
        result = 31 * result
                 + (electricityProductionNormalRateKwh != null ? electricityProductionNormalRateKwh.hashCode() : 0);
        result = 31 * result + (currentPowerConsumptionW != null ? currentPowerConsumptionW.hashCode() : 0);
        result = 31 * result + (currentPowerProductionW != null ? currentPowerProductionW.hashCode() : 0);
        result = 31 * result + (gasConsumptionM3 != null ? gasConsumptionM3.hashCode() : 0);
        return result;
    }
}
