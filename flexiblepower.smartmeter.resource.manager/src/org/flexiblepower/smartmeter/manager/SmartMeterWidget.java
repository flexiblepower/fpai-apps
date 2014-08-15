package org.flexiblepower.smartmeter.manager;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Locale;

import org.flexiblepower.ral.drivers.uncontrolled.UncontrolledState;
import org.flexiblepower.smartmeter.driver.interfaces.SmartMeterMeasurement;
import org.flexiblepower.smartmeter.driver.interfaces.SmartMeterState;
import org.flexiblepower.ui.Widget;

public class SmartMeterWidget implements Widget {

	public static class Update {
		
	    public boolean isConnected;
	    public BigDecimal electricityConsumptionLowRateKwh;
	    public BigDecimal electricityConsumptionNormalRateKwh;
	    public BigDecimal electricityProductionLowRateKwh;
	    public BigDecimal electricityProductionNormalRateKwh;
	    public BigDecimal currentPowerConsumptionW;
	    public BigDecimal currentPowerProductionW;
	    public BigDecimal gasConsumptionM3;

		public Update(){
		    isConnected = false;
		    electricityConsumptionLowRateKwh = new BigDecimal(0);
		    electricityConsumptionNormalRateKwh= new BigDecimal(0);
		    electricityProductionLowRateKwh= new BigDecimal(0);
		    electricityProductionNormalRateKwh= new BigDecimal(0);
		    currentPowerConsumptionW= new BigDecimal(0);
		    currentPowerProductionW= new BigDecimal(0);
		    gasConsumptionM3= new BigDecimal(0);
		}
		
		public Update(SmartMeterState state){
			this.isConnected = state.isConnected();
			this.electricityConsumptionLowRateKwh = state.getElectricityConsumptionLowRateKwh();
			this.electricityConsumptionNormalRateKwh = state.getElectricityConsumptionNormalRateKwh();
			this.electricityProductionLowRateKwh = state.getElectricityProductionLowRateKwh();
			this.electricityProductionNormalRateKwh = state.getElectricityProductionNormalRateKwh();
			this.currentPowerConsumptionW = state.getCurrentPowerConsumptionW();
			this.currentPowerProductionW = state.getCurrentPowerProductionW();
			this.gasConsumptionM3 = state.getGasConsumptionM3();
		}

		public boolean isConnected() {
			return isConnected;
		}

		public BigDecimal getElectricityConsumptionLowRateKwh() {
			return electricityConsumptionLowRateKwh;
		}

		public BigDecimal getElectricityConsumptionNormalRateKwh() {
			return electricityConsumptionNormalRateKwh;
		}

		public BigDecimal getElectricityProductionLowRateKwh() {
			return electricityProductionLowRateKwh;
		}

		public BigDecimal getElectricityProductionNormalRateKwh() {
			return electricityProductionNormalRateKwh;
		}

		public BigDecimal getCurrentPowerConsumptionW() {
			return currentPowerConsumptionW;
		}

		public BigDecimal getCurrentPowerProductionW() {
			return currentPowerProductionW;
		}

		public BigDecimal getGasConsumptionM3() {
			return gasConsumptionM3;
		}
	}

	private final SmartMeterManager smartMeterManager;

	public SmartMeterWidget(SmartMeterManager smartMeterManager) {
		this.smartMeterManager = smartMeterManager;
	}

	public Update update(Locale locale) {
		
		SmartMeterState state = smartMeterManager.getState();
		if (state != null) {
			return new Update(smartMeterManager.getState());
		} else {
			return new Update();
		}
	}

	@Override
	public String getTitle(Locale locale) {
		return "Smart Meter";
	}

	public SmartMeterManager getSmartMeterManager() {
		return smartMeterManager;
	}

}
