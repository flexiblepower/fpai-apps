package flexiblepower.manager.genericadvancedbattery;

public enum GenericAdvancedBatteryMode {
	IDLE(0), CHARGE(1), DISCHARGE(-1);

	public final int runningModeId;

	GenericAdvancedBatteryMode(int rmId) {
		runningModeId = rmId;
	}

	public static GenericAdvancedBatteryMode getByRunningModeId(int rmId) {
		for (GenericAdvancedBatteryMode m : GenericAdvancedBatteryMode.values()) {
			if (m.runningModeId == rmId) {
				return m;
			}
		}
		return null;
	}

}
