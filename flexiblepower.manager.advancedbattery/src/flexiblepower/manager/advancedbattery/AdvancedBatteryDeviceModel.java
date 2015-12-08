package flexiblepower.manager.advancedbattery;

import javax.measure.Measurable;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;

public class AdvancedBatteryDeviceModel implements Runnable {

	AdvancedBatteryMode mode;
	
	public AdvancedBatteryDeviceModel(AdvancedBatteryConfig configuration) {
		// TODO Auto-generated constructor stub
	}

	public AdvancedBatteryMode getCurrentMode() {
		return mode;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}

	public Measurable<Energy> getTotalCapacity() {
		// TODO Auto-generated method stub
		return null;
	}

	public double getChargeEfficiency(Measurable<Power> chargeSpeed) {
		// TODO Auto-generated method stub
		return 1;
	}

	public double getDischargeEfficiency(Measurable<Power> chargeSpeed) {
		// TODO Auto-generated method stub
		return 1;
	}

}
