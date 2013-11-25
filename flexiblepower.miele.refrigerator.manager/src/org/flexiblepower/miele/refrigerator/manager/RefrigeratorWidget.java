package org.flexiblepower.miele.refrigerator.manager;

import java.util.Locale;

import javax.measure.unit.SI;

import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorState;
import org.flexiblepower.ui.Widget;

public class RefrigeratorWidget implements Widget {

    public static class Update {
        private final boolean superCool;
        private final String temperature;
        private final String minimumTemperature;
        private final String maximumTemperature;

        public Update(boolean superCool, String temperature, String minimumTemperature, String maximumTemperature) {
            this.superCool = superCool;
            this.temperature = temperature;
            this.minimumTemperature = minimumTemperature;
            this.maximumTemperature = maximumTemperature;
        }

        public boolean isSuperCool() {
            return superCool;
        }

        public String getTemperature() {
            return temperature;
        }

        public String getMinimumTemperature() {
            return minimumTemperature;
        }

        public String getMaximumTemperature() {
            return maximumTemperature;
        }
    }

    private static final String TEMP = "%2.1f C";

    private final MieleRefrigeratorManager fridge;

    public RefrigeratorWidget(MieleRefrigeratorManager fridge) {
        this.fridge = fridge;
    }

    public MieleRefrigeratorManager getFridge() {
        return fridge;
    }

    public Update update(Locale locale) {
        RefrigeratorState state = fridge.getCurrentState();
        if (state != null) {
            return new Update(state.getSupercoolMode(),
                              String.format(locale, TEMP, state.getCurrentTemperature().doubleValue(SI.CELSIUS)),
                              String.format(locale, TEMP, state.getMinimumTemperature().doubleValue(SI.CELSIUS)),
                              String.format(locale, TEMP, state.getTargetTemperature().doubleValue(SI.CELSIUS)));
        } else {
            return new Update(false, "Not Available", "Not Available", "Not Available");
        }
    }

    @Override
    public String getTitle(Locale locale) {
        return "Miele@Home Fridge";
    }
}
