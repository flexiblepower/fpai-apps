package net.powermatcher.fpai.agents;

import org.flexiblepower.ral.messages.AllocationStatusUpdate;
import org.flexiblepower.ral.messages.ControlSpaceRegistration;
import org.flexiblepower.ral.messages.ControlSpaceRevoke;
import org.flexiblepower.ral.messages.ControlSpaceUpdate;

import net.powermatcher.api.AgentEndpoint;
import net.powermatcher.api.data.Bid;
import net.powermatcher.api.data.Price;
import net.powermatcher.api.messages.BidUpdate;
import net.powermatcher.api.messages.PriceUpdate;
import net.powermatcher.core.BaseAgentEndpoint;
import net.powermatcher.fpai.controller.AgentMessageSender;

/**
 * Provides the common logic, interfaces and fields for all types of FpaiAgents, like Buffer agents and Unconstrained
 * agents.
 *
 */
public abstract class FpaiAgent extends BaseAgentEndpoint implements Comparable<FpaiAgent> {

    /**
     * This lock makes sure that {@link FpaiAgent#createBid(net.powermatcher.api.AgentEndpoint.Status)} and
     * {@link FpaiAgent#handlePriceUpdate(Price)} cannot be executed in parallel.
     */
    private final Object lock = new Object();

    final AgentMessageSender messageSender;

    /**
     * Constructs an FpaiAgent based on the given messageSender.
     *
     * @param messageSender
     *            The {@link AgentMessageSender} that should be used to send messages
     * @param agentId
     *            The unique identifier of this agent
     * @param desiredParentId
     *            The identifier of the parent agent this agent wants to connect to
     */
    public FpaiAgent(AgentMessageSender messageSender, String agentId, String desiredParentId) {
        if (messageSender == null) {
            throw new NullPointerException("messageHandler");
        }

        this.messageSender = messageSender;
        init(agentId, desiredParentId);
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

    protected abstract Bid createBid(AgentEndpoint.Status currentStatus);

    protected void doBidUpdate() {
        AgentEndpoint.Status currentStatus = getStatus();
        if (currentStatus.isConnected()) {
            Bid bid = null;
            boolean publishBid = false;
            synchronized (lock) {
                bid = createBid(currentStatus);
                BidUpdate lastBidUpdate = getLastBidUpdate();
                if (bid != null && (lastBidUpdate == null || !bid.equals(lastBidUpdate.getBid()))) {
                    // The bid is not null and is not equal to the last bid
                    publishBid = true;
                }
            }
            if (publishBid) {
                publishBid(bid);
            }
        }
    }

    /**
     * Updates the internal PowerMatcher price field and calls the priceUpdated method to handle the new price.
     */
    @Override
    public final void handlePriceUpdate(PriceUpdate priceUpdate) {
        super.handlePriceUpdate(priceUpdate);
        BidUpdate lastBidUpdate = getLastBidUpdate();
        if (lastBidUpdate == null) {
            LOGGER.info("Ignoring price update while no bid has been sent");
        } else if (lastBidUpdate.getBidNumber() != priceUpdate.getBidNumber()) {
            LOGGER.info("Ignoring price update on old bid (lastBid={} priceUpdate={})",
                        lastBidUpdate.getBidNumber(),
                        priceUpdate.getBidNumber());
        } else {
            synchronized (lock) {
                handlePriceUpdate(priceUpdate.getPrice());
            }
        }
    }

    protected abstract void handlePriceUpdate(Price newPrice);

    @Override
    public int compareTo(FpaiAgent o) {
        return getAgentId().compareTo(o.getAgentId());
    }
}
