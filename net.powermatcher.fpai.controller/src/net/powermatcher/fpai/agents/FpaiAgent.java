package net.powermatcher.fpai.agents;

import net.powermatcher.api.data.Bid;
import net.powermatcher.api.data.Price;
import net.powermatcher.api.messages.PriceUpdate;
import net.powermatcher.core.BaseAgentEndpoint;
import net.powermatcher.fpai.controller.AgentTracker;

import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.MessageHandler;
import org.flexiblepower.ral.messages.Allocation;
import org.flexiblepower.ral.messages.AllocationRevoke;
import org.flexiblepower.ral.messages.AllocationStatusUpdate;
import org.flexiblepower.ral.messages.ControlSpaceRegistration;
import org.flexiblepower.ral.messages.ControlSpaceRevoke;
import org.flexiblepower.ral.messages.ControlSpaceUpdate;

/**
 * Provides the common logic, interfaces and fields for all types of FpaiAgents, like Buffer agents and Unconstrained
 * agents.
 *
 */
public abstract class FpaiAgent
    extends BaseAgentEndpoint
    implements MessageHandler, Comparable<FpaiAgent> {

    final Connection connection;
    final AgentTracker agentTracker;

    private final String agentPrefix;
    private final String desiredParentId;

    /**
     * Constructs an FpaiAgent based on the necessary connection, configuration and tracker information.
     *
     * @param connection
     *            The connection to the resource manager.
     * @param agentTracker
     *            The tracker of this agent.
     * @param agentPrefix
     *            The prefix for the identifier of this agent
     * @param desiredParentId
     *            The identifier of the matcher this agent should connect to
     */
    public FpaiAgent(Connection connection, AgentTracker agentTracker, String agentPrefix, String desiredParentId) {
        if (connection == null) {
            throw new NullPointerException("connection");
        } else if (agentTracker == null) {
            throw new NullPointerException("agentTracker");
        }

        this.connection = connection;
        this.agentTracker = agentTracker;

        this.agentPrefix = agentPrefix;
        this.desiredParentId = desiredParentId;
    }

    /**
     * Handles the reception of the different EFI messages based on the type of message.
     */
    @Override
    public synchronized void handleMessage(Object message) {
        if (message == null) {
            LOGGER.error("Received a null message");
        } else if (message instanceof ControlSpaceRegistration) {
            ControlSpaceRegistration registration = (ControlSpaceRegistration) message;
            String agentId = agentPrefix + registration.getResourceId();
            activate(agentId, desiredParentId);

            handleControlSpaceRegistration(registration);
        } else if (message instanceof ControlSpaceUpdate) {
            handleControlSpaceUpdate((ControlSpaceUpdate) message);
        } else if (message instanceof ControlSpaceRevoke) {
            handleControlSpaceRevoke((ControlSpaceRevoke) message);
        } else if (message instanceof AllocationStatusUpdate) {
            handleAllocationStatusUpdate((AllocationStatusUpdate) message);
        } else {
            LOGGER.error("Received unknown type of message: " + message);
        }
    }

    /**
     * Processes a registration message.
     *
     * @param message
     *            The registration message.
     */
    protected abstract void handleControlSpaceRegistration(ControlSpaceRegistration message);

    /**
     * Processes the ControlSpaceUpdate sent out by the resource manager.
     *
     * @param message
     *            The ControlSpaceUpdate message.
     */
    protected abstract void handleControlSpaceUpdate(ControlSpaceUpdate message);

    /**
     * Processes the control space revoke that the resource manager may send out.
     *
     * @param message
     *            The Revoke message.
     */
    protected abstract void handleControlSpaceRevoke(ControlSpaceRevoke message);

    /**
     * Processes the AllocationStatusUpdate sent by the resource manager.
     *
     * @param message
     *            The update of the allocation status.
     */
    protected abstract void handleAllocationStatusUpdate(AllocationStatusUpdate message);

    /**
     * Provides the logic of disconnecting the agent with the agent tracker.
     */
    @Override
    public void disconnected() {
        LOGGER.info("Agent " + getAgentId() + " disconnecting");
        deactivate();
        agentTracker.unregisterAgent(this);
    }

    /**
     * Sends the allocation to the resource manager.
     *
     * @param allocation
     *            The allocation message.
     */
    protected void sendAllocation(Allocation allocation) {
        connection.sendMessage(allocation);
    }

    /**
     * Sends the revoke of an allocation message to the resource manager.
     *
     * @param allocationRevoke
     *            The revoke message of the allocation.
     */
    protected void sendAllocationRevoke(AllocationRevoke allocationRevoke) {
        connection.sendMessage(allocationRevoke);
    }

    protected abstract Bid createBid();

    protected synchronized void doBidUpdate() {
        if (isInitialized()) {
            Bid bid = createBid();
            if (bid != null) {
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
