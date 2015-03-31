package org.flexiblepower.driver.pv.sma.widget;

import java.util.Locale;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.flexiblepower.driver.pv.sma.SmaInverterDriver;
import org.flexiblepower.driver.pv.sma.SmaInverterState;
import org.flexiblepower.ui.Widget;

public class SmaWidget implements Widget {

    public static class Update {
        private final double demand;
        private final double todayProduction;
        private final double lifetimeProduction;
        private final boolean sunUp;

        public Update(SmaInverterState smaInverterState) {
            if (smaInverterState.getCurrentUsage() != null) {
                demand = smaInverterState.getCurrentUsage().doubleValue(SI.WATT);
            } else {
                demand = 0;
            }

            if (smaInverterState.getTodayProduction() != null) {
                todayProduction = smaInverterState.getTodayProduction().doubleValue(NonSI.KWH);
            } else {
                todayProduction = 0;
            }

            if (smaInverterState.getLifetimeProduction() != null) {
                lifetimeProduction = smaInverterState.getLifetimeProduction().doubleValue(NonSI.KWH);
            } else {
                lifetimeProduction = 0;
            }

            sunUp = smaInverterState.isSunUp();
        }

        public double getDemand() {
            return demand;
        }

        public double getTodayProduction() {
            return todayProduction;
        }

        public double getLifetimeProduction() {
            return lifetimeProduction;
        }

        public boolean isSunUp() {
            return sunUp;
        }
    }

    private final SmaInverterDriver smaInverterDriver;

    public SmaWidget(SmaInverterDriver smaInverterDriver) {
        this.smaInverterDriver = smaInverterDriver;
    }

    @Override
    public String getTitle(Locale locale) {
        return "SMA Inverter";
    }

    public Update update() {
        SmaInverterState state = smaInverterDriver.getCurrentState();
        if (state == null) {
            return null;
        } else {
            return new Update(state);
        }
    }
}
