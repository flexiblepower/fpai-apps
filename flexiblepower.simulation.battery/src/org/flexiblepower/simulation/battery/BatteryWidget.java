package org.flexiblepower.simulation.battery;

import static javax.measure.unit.NonSI.KWH;

import java.util.Locale;

import org.flexiblepower.ral.drivers.battery.BatteryMode;
import org.flexiblepower.ral.drivers.battery.BatteryState;
import org.flexiblepower.ui.Widget;

public class BatteryWidget implements Widget {
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

    private final BatterySimulation simulation;

    public BatteryWidget(BatterySimulation simulation) {
        this.simulation = simulation;
    }

    public Update update() {
        BatteryState state = simulation.getCurrentState();
        if (state != null) {
            double soc = state.getStateOfCharge();
            int socPercentage = (int) (soc * 100.0);
            double capacity = state.getTotalCapacity().doubleValue(KWH);
            BatteryMode mode = state.getCurrentMode();
            System.out.println("ZEN_BAT " + String.valueOf(socPercentage));
            return new Update(socPercentage, String.format("%2.1f kWh", capacity), mode.toString());
        }
        return null;
    }

    @Override
    public String getTitle(Locale locale) {
        return "Storage Device Manager";
    }
}
