package flexiblepower.manager.advancedbattery;

import java.util.Locale;

import org.flexiblepower.ui.Widget;

public class AdvancedBatteryWidget implements Widget {
    public static class Update {
        private final int soc;
        private final String totalCapacity;
        private final String mode;

        public Update(int soc, String totalCapacity, String mode) {
            this.soc = soc;
            this.totalCapacity = totalCapacity;
            this.mode = mode;
        }

        public int getSoc() {
            return soc;
        }

        public String getTotalCapacity() {
            return totalCapacity;
        }

        public String getMode() {
            return mode;
        }
    }

    private final AdvancedBatteryDeviceModel deviceModel;

    public AdvancedBatteryWidget(AdvancedBatteryDeviceModel deviceModel) {
        this.deviceModel = deviceModel;
    }
	
    public Update update() {
//        double soc = deviceModel.getStateOfCharge();
//        int socPercentage = (int) (soc * 100.0);
//        double capacity = deviceModel.getTotalCapacity().doubleValue(KWH);
//        BatteryMode mode = deviceModel.getCurrentMode();
    	//TODO: Fill this
        return new Update(1, "", "");
    }

    @Override
    public String getTitle(Locale locale) {
        return "Advanced Battery Simulation";
    }
}
