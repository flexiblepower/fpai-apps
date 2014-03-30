package org.flexiblepower.uncontrolled.manager;

import java.util.Locale;

import javax.measure.Measurable;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.ui.Widget;

public class UncontrolledManagerWidget implements Widget {

    public static class Update {
        public final String resourceId;
        public final double demandWatts;

        public Update(String resourceId, double demandWatts) {
            super();
            this.resourceId = resourceId;
            this.demandWatts = demandWatts;
        }

        public String getResourceId() {
            return resourceId;
        }

        public double getWatts() {
            return demandWatts;
        }
    }

    private final UncontrolledManager manager;

    public UncontrolledManagerWidget(UncontrolledManager manager) {
        this.manager = manager;
    }

    @Override
    public String getTitle(Locale locale) {
        return "Uncontrolled manager";
    }

    public Update update() {
        Measurable<Power> lastDemand = manager.getLastDemand();
        if (lastDemand == null) {
            return new Update(manager.getResourceId(), 0);
        } else {
            return new Update(manager.getResourceId(), lastDemand.doubleValue(SI.WATT));
        }
    }

}
