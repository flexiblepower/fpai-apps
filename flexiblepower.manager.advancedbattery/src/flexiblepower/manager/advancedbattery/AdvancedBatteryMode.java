package flexiblepower.manager.advancedbattery;

public enum AdvancedBatteryMode {
	IDLE(0), CHARGE(1), DISCHARGE(-1);

	public final int runningModeId;

	AdvancedBatteryMode(int rmId) {
		runningModeId = rmId;
	}

	public static AdvancedBatteryMode getByRunningModeId(int rmId) {
		for (AdvancedBatteryMode m : AdvancedBatteryMode.values()) {
			if (m.runningModeId == rmId) {
				return m;
			}
		}
		return null;
	}

}
