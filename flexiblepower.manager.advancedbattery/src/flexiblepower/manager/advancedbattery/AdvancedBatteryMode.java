package flexiblepower.manager.advancedbattery;

public enum AdvancedBatteryMode {
	IDLE(0),
	CHARGE(1),
	DISCHARGE(-1);

	final int runningModeId;
	
	AdvancedBatteryMode(int rmId) {
		runningModeId = rmId;
	}
	
}
