package flexiblepower.manager.advancedbattery;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.flexiblepower.ral.drivers.battery.BatteryState;

public class AdvancedBatteryDeviceModel implements Runnable {

	AdvancedBatteryMode mode;
	private AdvancedBatteryConfig configuration;
	
	public AdvancedBatteryDeviceModel(AdvancedBatteryConfig configuration) {
		this.configuration = configuration;
	}

	public AdvancedBatteryMode getCurrentMode() {
		return mode;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}

	public Measurable<Energy> getTotalCapacity() {
		return Measure.valueOf(configuration.nrOfmodules() * 1.2, NonSI.KWH);
	}

	public double getChargeEfficiency(Measurable<Power> chargeSpeed) {
		// TODO Auto-generated method stub
		return 1;
	}

	public double getDischargeEfficiency(Measurable<Power> chargeSpeed) {
		// TODO Auto-generated method stub
		return 1;
	}
	
	public Measurable<Power> getMaximumChargeSpeed() {
		// TODO actually use
		return Measure.valueOf(configuration.nrOfmodules() <= 1 ? 2500 : 5000, SI.WATT);
	}

	public Measurable<Power> getMaximumDischargeSpeed() {
		// TODO actually use
		return Measure.valueOf(configuration.nrOfmodules() <= 1 ? -2500 : -5000, SI.WATT);
	}

	public Measurable<Dimensionless> getCurrentFillLevel() {
		// TODO Auto-generated method stub
		return null;
	}
	
}
