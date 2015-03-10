package net.powermatcher.fpai.agents;

import net.powermatcher.api.data.Bid;
import net.powermatcher.api.data.Price;
import net.powermatcher.api.messages.BidUpdate;
import net.powermatcher.api.messages.PriceUpdate;
import net.powermatcher.core.BaseAgentEndpoint;
import net.powermatcher.fpai.controller.AgentMessageSender;

import org.flexiblepower.ral.messages.AllocationStatusUpdate;
import org.flexiblepower.ral.messages.ControlSpaceRegistration;
import org.flexiblepower.ral.messages.ControlSpaceRevoke;
import org.flexiblepower.ral.messages.ControlSpaceUpdate;

/**
 * Provides the common logic, interfaces and fields for all types of FpaiAgents, like Buffer agents and Unconstrained
 * agents.
 *
 */
public abstract class FpaiAgent extends BaseAgentEndpoint implements Comparable<FpaiAgent> {

    final AgentMessageSender messageSender;

    /**
     * Constructs an FpaiAgent based on the given messageSender.
     *
     * @param messageSender
     *            The {@link AgentMessageSender} that should be used to send messages
     */
    public FpaiAgent(AgentMessageSender messageSender) {
        if (messageSender == null) {
            throw new NullPointerException("messageHandler");
        }

        this.messageSender = messageSender;
    }

    /**
     * Processes a registration message.
     *
     * @param message
     *            The registration message.
     */
    public abstract void handleControlSpaceRegistration(ControlSpaceRegistration message);

    /**
     * Processes the ControlSpaceUpdate sent out by the resource manager.
     *
     * @param message
     *            The ControlSpaceUpdate message.
     */
    public abstract void handleControlSpaceUpdate(ControlSpaceUpdate message);

    /**
     * Processes the control space revoke that the resource manager may send out.
     *
     * @param message
     *            The Revoke message.
     */
    public abstract void handleControlSpaceRevoke(ControlSpaceRevoke message);

    /**
     * Processes the AllocationStatusUpdate sent by the resource manager.
     *
     * @param message
     *            The update of the allocation status.
     */
    public abstract void handleAllocationStatusUpdate(AllocationStatusUpdate message);

    protected abstract Bid createBid();

    protected synchronized void doBidUpdate() {
        if (isInitialized()) {
            Bid bid = createBid();
            BidUpdate lastBidUpdate = getLastBidUpdate();
            if (bid != null && (lastBidUpdate == null || !bid.equals(lastBidUpdate.getBid()))) {
                publishBid(bid);
            }
        }
    }

    /**
     * Updates the internal PowerMatcher price field and calls the priceUpdated method to handle the new price.
     */
    @Override
    public synchronized final void handlePriceUpdate(PriceUpdate priceUpdate) {
        super.handlePriceUpdate(priceUpdate);
        if (getLastBidUpdate() == null) {
            LOGGER.info("Ignoring price update while no bid has been sent");
        } else if (getLastBidUpdate().getBidNumber() != priceUpdate.getBidNumber()) {
            LOGGER.info("Ignoring price update on old bid (lastBid={} priceUpdate={})",
                        getLastBidUpdate().getBidNumber(),
                        priceUpdate.getBidNumber());
        } else {
            handlePriceUpdate(priceUpdate.getPrice());
        }
    }

    protected abstract void handlePriceUpdate(Price newPrice);

    @Override
    public int compareTo(FpaiAgent o) {
        return getAgentId().compareTo(o.getAgentId());
    }
}
