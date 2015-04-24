package org.flexiblepower.uncontrolled.simulation;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import javax.measure.unit.SI;

import org.flexiblepower.ui.Widget;

public class UCWidget implements Widget {
    public static class Update {
        private final String dateTime;
        private final String supply;
        private final String weather;

        public Update(String dateTime, String supply, String weather) {
            this.dateTime = dateTime;
            this.supply = supply;
            this.weather = weather;
        }

        public String getDateTime() {
            return dateTime;
        }

        public String getSupply() {
            return supply;
        }

        public String getWeather() {
            return weather;
        }
    }

    private static final DateFormat FORMATTER = new SimpleDateFormat("HH:mm:ss");

    private final UncontrolledSimulation simulation;

    public UCWidget(UncontrolledSimulation simulation) {
        this.simulation = simulation;
    }

    public Update update() {
        PowerStateImpl state = simulation.getCurrentState();
        return new Update(FORMATTER.format(state.getTime()),
                          Integer.toString((int) state.getCurrentUsage().doubleValue(SI.WATT)),
                          simulation.getDevice().toString());
    }

    public Update changeWeather() {
        Devices oldDevice = simulation.getDevice();
        Devices newDevice = null;
        switch (oldDevice) {
        case off:
            newDevice = Devices.tv;
            break;
        case tv:
            newDevice = Devices.coffee;
            break;
        default:
            newDevice = Devices.off;
        }

        simulation.setDevice(newDevice);
        return update();
    }

    @Override
    public String getTitle(Locale locale) {
        return "Uncontrolled Simulation";
    }
}
