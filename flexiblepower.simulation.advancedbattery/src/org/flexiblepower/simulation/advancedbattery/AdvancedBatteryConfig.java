package org.flexiblepower.simulation.advancedbattery;

import aQute.bnd.annotation.metatype.Meta;

public interface AdvancedBatteryConfig {
	@Meta.AD(deflt = "5", description = "Interval between state updates [s]")
	long updateInterval();

	@Meta.AD(deflt = "1", description = "Total capacity [kWh]")
	double totalCapacity();

	@Meta.AD(deflt = "0.5", description = "Initial state of charge (from 0 to 1)")
	double initialStateOfCharge();

	@Meta.AD(deflt = "1500", description = "Charge power [W]")
	long chargePower();

	@Meta.AD(deflt = "1500", description = "Discharge power [W]")
	long dischargePower();

	@Meta.AD(deflt = "0.9", description = "Charge efficiency (from 0 to 1)")
	double chargeEfficiency();

	@Meta.AD(deflt = "0.9", description = "Discharge efficiency (from 0 to 1)")
	double dischargeEfficiency();

	@Meta.AD(deflt = "50", description = "Self discharge power [W]")
	long selfDischargePower();
}
