package org.flexiblepower.simulation.advancedbattery;

import static javax.measure.unit.NonSI.KWH;

import java.util.Locale;

import org.flexiblepower.ral.drivers.battery.BatteryMode;
import org.flexiblepower.simulation.advancedbattery.AdvancedBatterySimulation.State;
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

    private final AdvancedBatterySimulation simulation;

    public AdvancedBatteryWidget(AdvancedBatterySimulation simulation) {
        this.simulation = simulation;
    }
	
    public Update update() {
        State state = simulation.getCurrentState();
        double soc = state.getStateOfCharge();
        int socPercentage = (int) (soc * 100.0);
        double capacity = state.getTotalCapacity().doubleValue(KWH);
        BatteryMode mode = state.getCurrentMode();
        return new Update(socPercentage, String.format("%2.1f kWh", capacity), mode.toString());
    }

    @Override
    public String getTitle(Locale locale) {
        return "Advanced Battery Simulation";
    }
}
