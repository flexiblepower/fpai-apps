package org.flexiblepower.driver.zenobox;

import java.util.Date;

import javax.measure.Measurable;
import javax.measure.quantity.Power;

import org.flexiblepower.ral.drivers.uncontrolled.PowerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PowerStateImpl implements PowerState {
    private final Measurable<Power> demand;
    private final Date time;
    private static final Logger logger = LoggerFactory.getLogger(Zenobox.class);

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
        logger.info("Usage requested: " + demand.toString());
        return demand;
    }

    public Date getTime() {
        return time;
    }
}
