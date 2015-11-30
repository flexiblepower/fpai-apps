package org.flexiblepower.simulation.heatpump.daikin;

import java.util.Locale;

import javax.measure.Measurable;
import javax.measure.quantity.Temperature;
import javax.measure.unit.SI;

import org.flexiblepower.ral.drivers.heatpump.HeatpumpState;
import org.flexiblepower.ui.Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeatpumpWidget implements Widget {
    public static class Update {
        private final double temp;
        private final String mode;

        public Update(Measurable<Temperature> temp, String mode) {
            this.temp = temp.doubleValue(SI.CELSIUS);
            this.mode = mode;
        }

        public Double getTemp() {
            return temp;
        }

        public String getMode() {
            return mode;
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(HeatpumpSimulation.class);
    private final HeatpumpSimulation simulation;

    public HeatpumpWidget(HeatpumpSimulation simulation) {
        this.simulation = simulation;
    }

    public Update update() {
        HeatpumpState state = simulation.getCurrentState();
        if (state == null) {
            return null;
        }

        Measurable<Temperature> temp = state.getCurrentTemperature();
        boolean mode = state.getHeatMode();
        String dispMode = "";
        if (mode) {
            dispMode = "Heating";
        }
        if (mode == false) {
            dispMode = "Idle";
        }
        logger.info("temp : " + temp);
        logger.info("dMode : " + dispMode);
        return new Update(temp, dispMode);
    }

    @Override
    public String getTitle(Locale locale) {
        return "Heatpump Manager";
    }
}
