package net.powermatcher.fpai.agents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.measure.quantity.Quantity;
import javax.measure.unit.SI;

import net.powermatcher.api.data.Bid;
import net.powermatcher.api.data.MarketBasis;
import net.powermatcher.api.data.PointBid;
import net.powermatcher.api.data.Price;
import net.powermatcher.api.data.PricePoint;
import net.powermatcher.fpai.agents.BufferBid.BufferBidElement;
import net.powermatcher.fpai.controller.AgentTracker;

import org.flexiblepower.api.efi.bufferhelper.Buffer;
import org.flexiblepower.api.efi.bufferhelper.BufferActuator;
import org.flexiblepower.efi.buffer.ActuatorAllocation;
import org.flexiblepower.efi.buffer.BufferAllocation;
import org.flexiblepower.efi.buffer.BufferRegistration;
import org.flexiblepower.efi.buffer.BufferStateUpdate;
import org.flexiblepower.efi.buffer.BufferSystemDescription;
import org.flexiblepower.efi.buffer.BufferTargetProfileUpdate;
import org.flexiblepower.efi.buffer.BufferUsageForecast;
import org.flexiblepower.efi.buffer.RunningModeBehaviour;
import org.flexiblepower.efi.util.FillLevelFunction;
import org.flexiblepower.efi.util.RunningMode;
import org.flexiblepower.messaging.Connection;
import org.flexiblepower.ral.messages.AllocationStatusUpdate;
import org.flexiblepower.ral.messages.ControlSpaceRegistration;
import org.flexiblepower.ral.messages.ControlSpaceRevoke;
import org.flexiblepower.ral.messages.ControlSpaceUpdate;
import org.flexiblepower.ral.values.Commodity;

/**
 * The BufferAgent constructs PowerMatcher bids and processes returned prices into allocations for the resource manager.
 *
 * @param <Q>
 *            The physical quantity that this Buffer stores and that belongs to the unit in which the fill level is
 *            expressed.
 */
public class BufferAgent<Q extends Quantity> extends FpaiAgent {

    private BufferRegistration<Q> registration;
    private Buffer<Q> bufferHelper;
    private BufferBid lastBid;
    private BufferTargetProfileUpdate<Q> lastBufferTargetProfile;
    private BufferSystemDescription lastBufferSystemDescription;

    public BufferAgent(Connection connection, AgentTracker agentTracker, String agentId, String desiredParentId) {
        super(connection, agentTracker, agentId, desiredParentId);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void handleControlSpaceRegistration(ControlSpaceRegistration message) {
        if (message instanceof BufferRegistration) {
            if (registration == null) {
                registration = (BufferRegistration<Q>) message;
                bufferHelper = new Buffer<Q>(registration);
            } else {
                LOGGER.error("Received multiple ControlSpaceRegistrations, ignoring...");
            }
        } else {
            LOGGER.error("Received unknown ControlSpaceRegistration: " + message);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void handleControlSpaceUpdate(ControlSpaceUpdate message) {
        if (message instanceof BufferSystemDescription) {
            bufferHelper.processSystemDescription((BufferSystemDescription) message);
            lastBufferSystemDescription = (BufferSystemDescription) message;
            // A new SystemDescription does not trigger a bid update.
        } else if (message instanceof BufferStateUpdate) {
            bufferHelper.processStateUpdate((BufferStateUpdate<Q>) message);
            doBidUpdate();
        } else if (message instanceof BufferTargetProfileUpdate) {
            lastBufferTargetProfile = (BufferTargetProfileUpdate<Q>) message;
            doBidUpdate();
        } else if (message instanceof BufferUsageForecast) {
            // TODO
            LOGGER.info("BufferUsageForecast not yet supported");
        } else {
            LOGGER.info("ControlSpaceUpdate not yet supported: " + message.getClass().getName());
        }
    }

    @Override
    protected void handleAllocationStatusUpdate(AllocationStatusUpdate message) {
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
    protected void handleControlSpaceRevoke(ControlSpaceRevoke message) {
        // TODO Auto-generated method stub

    }

    @Override
    protected Bid createBid() {
        MarketBasis marketBasis = getMarketBasis();
        if (marketBasis == null || registration == null
            || !bufferHelper.hasReceivedStateUpdate()
            || !bufferHelper.hasReceivedSystemDescription()) {
            LOGGER.error("Not sending a bid, because not enough information has been received to form a bid (marketbasis, registration, stateupdate, systemdescription).");
            return null;
        }

        double soc = bufferHelper.getCurrentFillFraction();
        double priority = calculatePriority(soc);

        // TODO for now we only support one buffer actuator.
        BufferActuator<Q> actuator = bufferHelper.getElectricalActuators().get(0);

        Date now = now();
        Collection<RunningMode<FillLevelFunction<RunningModeBehaviour>>> runningModes = actuator.getReachableRunningModes(now);

        if (runningModes.isEmpty()) {
            LOGGER.error("No reachable running mode found, sending must off bid.");
            return new PointBid.Builder(marketBasis).add(new PricePoint(getMarketBasis(), 0, 0)).build();
        }

        double fillLevel = bufferHelper.getCurrentFillLevel().doubleValue(registration.getFillLevelUnit());
        final double minimum = actuator.getMinimumFillLevel();
        if (fillLevel < minimum) {
            LOGGER.info("Fill level is below minimum of this actuator's running modes. Assuming minimum fill level.");
            fillLevel = actuator.getMinimumFillLevel();
        }

        final double maximum = actuator.getMaximumFillLevel();
        if (fillLevel > maximum) {
            LOGGER.info("Fill level is above maximum of this actuator's running modes. Assuming maximum fill level.");
            fillLevel = maximum;
        }

        List<BufferBidElement> elements = new ArrayList<BufferBidElement>();
        for (RunningMode<FillLevelFunction<RunningModeBehaviour>> rm : runningModes) {
            try {
                if (fillLevel < actuator.getMinimumFillLevel()) {
                    throw new IllegalArgumentException("The current fill level is below the minimum fill level of this actuator, so this running mode is not an option.");
                }
                double demandWatt = rm.getValue()
                                      .getRangeElementForFillLevel(fillLevel)
                                      .getValue()
                                      .getCommodityConsumption()
                                      .get(Commodity.ELECTRICITY)
                                      .doubleValue(SI.WATT);

                BufferBidElement bbe = new BufferBidElement(actuator.getActuatorId(), rm.getId(), demandWatt);
                elements.add(bbe);
            } catch (IllegalArgumentException e) {
                // do nothing and continue.
            }
        }

        if (elements.isEmpty()) {
            LOGGER.debug("Due to fill level no reachable running mode was found, sending must off bid.");
            return new PointBid.Builder(marketBasis).add(new PricePoint(getMarketBasis(), 0, 0)).build();
        }

        // TODO: Check for concurrency problems with lastBid...
        lastBid = new BufferBid(marketBasis, elements, priority);
        LOGGER.info("Sending bid");
        return lastBid.toBid();
    }

    private double calculatePriority(double soc) {
        if (lastBufferTargetProfile != null) {
            TargetProfileHelper<Q> target = new TargetProfileHelper<Q>(lastBufferTargetProfile,
                                                                       lastBufferTargetProfile.getTargetProfile(),
                                                                       registration,
                                                                       lastBufferSystemDescription,
                                                                       bufferHelper);
            Date now = now();
            if (target.targetIsValid(now)) {
                return target.calculatePriority(now);
            } else {
                LOGGER.warn("Target is not valid, using normal bidding strategy");
            }
        }
        // Default behavior without target
        return 1 - 2 * soc;
    }

    @Override
    protected void handlePriceUpdate(Price newPrice) {
        if (lastBid != null && lastBufferSystemDescription != null) {
            BufferBidElement runningMode = lastBid.runningModeForPrice(newPrice);
            Date now = now();
            ActuatorAllocation actuatorAllocation = new ActuatorAllocation(runningMode.getActuatorId(),
                                                                           runningMode.getRunningModeId(),
                                                                           now);
            BufferAllocation allocation = new BufferAllocation(lastBufferSystemDescription,
                                                               now,
                                                               false,
                                                               Collections.singleton(actuatorAllocation));
            LOGGER.info("Sending allocation " + allocation);
            sendAllocation(allocation);
        } else {
            LOGGER.info("Received price update, but don't have enough info to construct allocation");
        }
    }
}
