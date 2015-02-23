package net.powermatcher.fpai.agents;

import javax.measure.Measurable;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import net.powermatcher.api.data.Bid;
import net.powermatcher.api.data.MarketBasis;
import net.powermatcher.api.data.Price;
import net.powermatcher.fpai.controller.AgentTracker;

import org.flexiblepower.efi.uncontrolled.UncontrolledForecast;
import org.flexiblepower.efi.uncontrolled.UncontrolledMeasurement;
import org.flexiblepower.efi.uncontrolled.UncontrolledRegistration;
import org.flexiblepower.messaging.Connection;
import org.flexiblepower.ral.messages.AllocationStatusUpdate;
import org.flexiblepower.ral.messages.ControlSpaceRegistration;
import org.flexiblepower.ral.messages.ControlSpaceRevoke;
import org.flexiblepower.ral.messages.ControlSpaceUpdate;
import org.flexiblepower.ral.values.Commodity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UncontrolledAgent extends FpaiAgent {
    private static final Logger logger = LoggerFactory.getLogger(UncontrolledAgent.class);

    private UncontrolledRegistration registration;
    private UncontrolledMeasurement lastUncontrolledMeasurement;

    public UncontrolledAgent(Connection connection, AgentTracker agentTracker, String agentId, String desiredParentId) {
        super(connection, agentTracker, agentId, desiredParentId);
    }

    @Override
    protected void handleControlSpaceRegistration(ControlSpaceRegistration message) {
        if (message instanceof UncontrolledRegistration) {
            if (registration == null) {
                registration = (UncontrolledRegistration) message;
                if (!registration.supportsCommodity(Commodity.ELECTRICITY)) {
                    logger.error("PowerMatcher cannot support appliances which do not support electricity");
                    disconnected();
                }
            } else {
                logger.error("Received multiple ControlSpaceRegistrations, ignoring...");
            }
        } else {
            logger.error("Received unknown ContorlSpaceRegistration: " + message);
        }
    }

    @Override
    protected void handleControlSpaceUpdate(ControlSpaceUpdate message) {
        if (message instanceof UncontrolledMeasurement) {
            lastUncontrolledMeasurement = (UncontrolledMeasurement) message;
            doBidUpdate();
        } else if (message instanceof UncontrolledForecast) {
            logger.debug("Received UncontrolledForecast, ignoring...");
        } else {
            logger.error("Received unknown type of ControlSpaceUpdate: " + message);
        }
    }

    @Override
    protected void handleAllocationStatusUpdate(AllocationStatusUpdate message) {
        logger.debug("Received AllocationStatusUpdate, ignoring...");
    }

    @Override
    protected void handleControlSpaceRevoke(ControlSpaceRevoke message) {
        // We don't support forecasts, so...
    }

    @Override
    protected Bid createBid() {
        MarketBasis marketBasis = getMarketBasis();
        if (lastUncontrolledMeasurement == null || marketBasis == null) {
            return null;
        } else {
            Measurable<Power> demand = lastUncontrolledMeasurement.getMeasurable().get(Commodity.ELECTRICITY);
            if (demand != null) {
                return Bid.flatDemand(marketBasis, demand.doubleValue(SI.WATT));
            } else {
                return null;
            }
        }

    }

    @Override
    protected void handlePriceUpdate(Price newPrice) {
        // Nothing to do for now
        // TODO: support curtailment
    }
}
