package net.powermatcher.fpai.agents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.measure.unit.SI;

import net.powermatcher.api.data.Bid;
import net.powermatcher.api.data.MarketBasis;
import net.powermatcher.api.data.Price;
import net.powermatcher.fpai.agents.BufferBid.BufferBidElement;
import net.powermatcher.fpai.controller.AgentMessageSender;

import org.flexiblepower.api.efi.unconstrainedhelper.Unconstrained;
import org.flexiblepower.efi.unconstrained.RunningModeBehaviour;
import org.flexiblepower.efi.unconstrained.RunningModeSelector;
import org.flexiblepower.efi.unconstrained.UnconstrainedAllocation;
import org.flexiblepower.efi.unconstrained.UnconstrainedRegistration;
import org.flexiblepower.efi.unconstrained.UnconstrainedStateUpdate;
import org.flexiblepower.efi.unconstrained.UnconstrainedSystemDescription;
import org.flexiblepower.efi.unconstrained.UnconstrainedUpdate;
import org.flexiblepower.efi.util.RunningMode;
import org.flexiblepower.ral.messages.Allocation;
import org.flexiblepower.ral.messages.AllocationStatusUpdate;
import org.flexiblepower.ral.messages.ControlSpaceRegistration;
import org.flexiblepower.ral.messages.ControlSpaceRevoke;
import org.flexiblepower.ral.messages.ControlSpaceUpdate;
import org.flexiblepower.ral.values.Commodity;

public class UnconstrainedAgent extends FpaiAgent {
    /** Priority used for BufferBid */
    private static final int BUFFER_BID_PRIORITY = 0;

    private UnconstrainedRegistration registration;
    private Unconstrained unconstrainedHelper;
    private BufferBid lastBid;
    private ControlSpaceUpdate lastControlSpaceUpdate;

    public UnconstrainedAgent(AgentMessageSender handler) {
        super(handler);
    }

    @Override
    public void handleControlSpaceRegistration(ControlSpaceRegistration message) {
        if (message instanceof UnconstrainedRegistration) {
            if (registration == null) {
                registration = (UnconstrainedRegistration) message;
                unconstrainedHelper = new Unconstrained(registration);
            } else {
                LOGGER.error("Received multiple ControlSpaceRegistrations, ignoring...");
            }
        } else {
            LOGGER.error("Received unknown ControlSpaceRegistration: " + message);
        }
    }

    @Override
    public void handleControlSpaceUpdate(ControlSpaceUpdate message) {
        if (message instanceof UnconstrainedSystemDescription) {
            unconstrainedHelper.processSystemDescription((UnconstrainedSystemDescription) message);
            lastControlSpaceUpdate = message;
            // A new SystemDescription does not trigger a bid update.
        } else if (message instanceof UnconstrainedStateUpdate) {
            unconstrainedHelper.processStateUpdate((UnconstrainedStateUpdate) message);
            lastControlSpaceUpdate = message;
            doBidUpdate();
        } else {
            LOGGER.info("This type of ControlSpaceUpdate is not supported.");
        }
    }

    @Override
    public void handleAllocationStatusUpdate(AllocationStatusUpdate message) {
        switch (message.getStatus()) {
        case ACCEPTED:
            // No action
            break;
        case REJECTED:
            // No action
            break;
        case PROCESSING:
            // No action
            break;
        case STARTED:
            // No action
            break;
        case FINISHED:
            // TODO here we can also make a transition
            break;
        }
        LOGGER.info("handleAllocationStatusUpdate not yet implemented");
        doBidUpdate();
    }

    @Override
    public void handleControlSpaceRevoke(ControlSpaceRevoke message) {
        // Return to no-flexibility-state
        lastControlSpaceUpdate = null;
    }

    @Override
    protected Bid createBid() {
        MarketBasis marketBasis = getMarketBasis();
        if (marketBasis == null || registration == null
            || !unconstrainedHelper.hasReceivedStateUpdate()
            || !unconstrainedHelper.hasReceivedSystemDescription()) {
            LOGGER.error("Not enough info to construct bid.");
            return null;
        }
        Date now = now();
        Collection<RunningMode<RunningModeBehaviour>> runningModes = unconstrainedHelper.getReachableRunningModes(now);

        if (runningModes.isEmpty()) {
            LOGGER.error("No reachable running mode found, sending must off bid");
            return Bid.flatDemand(marketBasis, 0);
        }

        List<BufferBidElement> elements = new ArrayList<BufferBidElement>();
        for (RunningMode<RunningModeBehaviour> rm : runningModes) {
            // ActuatorId is not relevant for the UnconstrainedAgent
            elements.add(new BufferBidElement(0, rm.getId(), rm.getValue()
                                                               .getCommodityConsumption()
                                                               .get(Commodity.ELECTRICITY)
                                                               .doubleValue(SI.WATT)));
        }
        lastBid = new BufferBid(marketBasis, elements, BUFFER_BID_PRIORITY);
        LOGGER.info("Sending bid");
        return lastBid.toBid();
    }

    @Override
    protected void handlePriceUpdate(Price newPrice) {
        if (lastBid != null && lastControlSpaceUpdate != null) {
            BufferBidElement runningMode = lastBid.runningModeForPrice(newPrice);
            Date now = now();

            RunningModeSelector runningModeSelector = new RunningModeSelector(runningMode.getRunningModeId(), now);
            Allocation allocation = new UnconstrainedAllocation((UnconstrainedUpdate) lastControlSpaceUpdate,
                                                                now,
                                                                false,
                                                                Collections.singleton(runningModeSelector));
            LOGGER.info("Sending allocation " + allocation);
            messageSender.sendMessage(allocation);
        } else {
            LOGGER.info("Received price update, but there is no previous bid or there is no previous control space update. So no allocation can be constructed.");
        }
    }
}
