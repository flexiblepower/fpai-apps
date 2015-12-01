package org.flexiblepower.simulation.rex;

import java.util.Locale;

import javax.measure.Measurable;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.simulation.rex.REXSimulation.PowerStateImpl;
import org.flexiblepower.ui.Widget;

import aQute.bnd.annotation.component.Component;

@Component(provide = Widget.class, properties = "widget.ranking=99")
public class REXWidget implements Widget {
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

    // private static final DateFormat FORMATTER = new SimpleDateFormat("HH:mm:ss");

    private final REXSimulation simulation;

    public REXWidget(REXSimulation simulation) {
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
        return "R.E.X. Controller";
    }

}
