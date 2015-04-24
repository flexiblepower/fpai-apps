package org.flexiblepower.uncontrolled.simulation;

import java.util.Date;

import javax.measure.Measurable;
import javax.measure.quantity.Power;

import org.flexiblepower.ral.drivers.uncontrolled.PowerState;

public final class PowerStateImpl implements PowerState {
    private final Measurable<Power> demand;
    private final Date time;

    public PowerStateImpl(Measurable<Power> demand, Date time) {
        this.demand = demand;
        this.time = time;
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public Measurable<Power> getCurrentUsage() {
        return demand;
    }

    public Date getTime() {
        return time;
    }
}
