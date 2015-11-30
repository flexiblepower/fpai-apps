package org.flexiblepower.simulation.rexManual;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import javax.measure.Measurable;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.simulation.rexManual.REXManualSimulation.PowerStateImpl;
import org.flexiblepower.ui.Widget;

public class REXManualWidget implements Widget {
    public static class Update {
        private final double price;
        private final double demand;

        public Update(double price, Measurable<Power> demand) {
            this.price = price;
            this.demand = demand.doubleValue(SI.WATT);
        }

        public double getPrice() {
            return price;
        }

        public double getDemand() {
            return demand;
        }

    }

    private static final DateFormat FORMATTER = new SimpleDateFormat("HH:mm:ss");

    private final REXManualSimulation simulation;

    public REXManualWidget(REXManualSimulation simulation) {
        this.simulation = simulation;
    }

    public Update update() {
        PowerStateImpl state = simulation.getCurrentState();

        if (state == null) {
            return null;
        }

        double price = state.getPrice();
        Measurable<Power> demand = state.getCurrentUsage();

        return new Update(price, demand);
    }

    @Override
    public String getTitle(Locale locale) {
        return "R.E.X. Imbalance";
    }

    public void setDemand(String demand) {
        simulation.setDemand(demand);
        // return new Update("", price);
    }

}
