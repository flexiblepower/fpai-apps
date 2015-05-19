package org.flexiblepower.miele.refrigerator.driver;

import java.util.Locale;

import javax.measure.unit.SI;

import org.flexiblepower.miele.refrigerator.driver.RefrigeratorDriver.State;
import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorState;
import org.flexiblepower.ui.Widget;

public class RefrigeratorWidget implements Widget {

    public static class Update {

        private final double currentTemperature;
        private final double targetTemerature;
        private final boolean superCool;
        private final boolean ready;

        public Update(double currentTemperature, double targetTemerature, boolean superCool) {
            this.currentTemperature = currentTemperature;
            this.targetTemerature = targetTemerature;
            this.superCool = superCool;
            ready = true;
        }

        public Update(RefrigeratorState state) {
            this(state.getCurrentTemperature().doubleValue(SI.CELSIUS),
                 state.getTargetTemperature().doubleValue(SI.CELSIUS),
                 state.getSupercoolMode());
        }

        public Update() {
            currentTemperature = 0;
            targetTemerature = 0;
            superCool = false;
            ready = false;
        }

        public double getCurrentTemperature() {
            return currentTemperature;
        }

        public double getTargetTemerature() {
            return targetTemerature;
        }

        public boolean isSuperCool() {
            return superCool;
        }

    }

    private final RefrigeratorDriver refrigerator;

    public RefrigeratorWidget(RefrigeratorDriver refrigerator) {
        this.refrigerator = refrigerator;

    }

    public RefrigeratorDriver getDishwasher() {
        return refrigerator;
    }

    public Update update(Locale locale) {
        State state = refrigerator.getCurrentState();
        if (state == null) {
            return new Update();
        } else {
            return new Update(state);
        }
    }

    @Override
    public String getTitle(Locale locale) {
        return "Miele@Home Refrigerator";
    }
}
