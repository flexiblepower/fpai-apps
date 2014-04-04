package org.flexiblepower.heatpump.simulation;

import java.text.DecimalFormat;
import java.util.Locale;

import org.flexiblepower.ui.Widget;

public class HeatPumpSimulationWidget implements Widget {

    public static class Update {
        String status;
        String roomTemp;
        String minTemp;
        String maxTemp;
        String power;

        public Update(String status, double roomTemp, double minTemp, double maxTemp, double power) {
            this.status = status;
            this.roomTemp = df.format(roomTemp);
            this.minTemp = df.format(minTemp);
            this.maxTemp = df.format(maxTemp);
            this.power = df.format(power);
        }

        public String getStatus() {
            return status;
        }

        public String getRoomTemp() {
            return roomTemp;
        }

        public String getMinTemp() {
            return minTemp;
        }

        public String getMaxTemp() {
            return maxTemp;
        }

        public String getPower() {
            return power;
        }

    }

    private static final DecimalFormat df = new DecimalFormat("#.##");
    private final HeatPumpSimulation simulation;

    public HeatPumpSimulationWidget(HeatPumpSimulation simulation) {
        this.simulation = simulation;
    }

    @Override
    public String getTitle(Locale locale) {
        return "Heatpump Simulation";
    }

    public Update update() {
        return new Update(simulation.getMode(),
                          simulation.getCurrentTemperature(),
                          simulation.getSetTemperature() - simulation.getTemperatureRange(),
                          simulation.getSetTemperature() + simulation.getTemperatureRange(),
                          simulation.getCurrentDemandWatt());
    }
}
