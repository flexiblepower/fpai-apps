package net.powermatcher.fpai.agents;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import net.powermatcher.api.data.ArrayBid;
import net.powermatcher.api.data.Bid;
import net.powermatcher.api.data.MarketBasis;
import net.powermatcher.api.data.PointBid;
import net.powermatcher.api.data.Price;
import net.powermatcher.fpai.controller.AgentMessageSender;

import org.flexiblepower.efi.timeshifter.SequentialProfile;
import org.flexiblepower.efi.timeshifter.SequentialProfileAllocation;
import org.flexiblepower.efi.timeshifter.TimeShifterAllocation;
import org.flexiblepower.efi.timeshifter.TimeShifterRegistration;
import org.flexiblepower.efi.timeshifter.TimeShifterUpdate;
import org.flexiblepower.ral.messages.AllocationStatusUpdate;
import org.flexiblepower.ral.messages.ControlSpaceRegistration;
import org.flexiblepower.ral.messages.ControlSpaceRevoke;
import org.flexiblepower.ral.messages.ControlSpaceUpdate;
import org.flexiblepower.ral.values.Commodity;
import org.flexiblepower.ral.values.CommodityForecast;
import org.flexiblepower.ral.values.CommodityUncertainMeasurables;
import org.flexiblepower.ral.values.Profile.Element;
import org.flexiblepower.time.TimeUtil;

public class TimeshifterAgent extends FpaiAgent implements Runnable {

    private static final Unit<Duration> MS = SI.MILLI(SI.SECOND);
    private static final int RUNNING_PROFILE_BID_UPDATE_INTERVAL_MS = 10000;
    private static final double EAGERNESS = 1.0;

    private TimeShifterRegistration registration;

    /** Last received {@link TimeShifterUpdate}. Null means no flexibility, must not run bid. */
    private TimeShifterUpdate lastTimeshifterUpdate = null;
    private CommodityForecast concatenatedCommodityForecast;

    /** Time when the machine started. Null means it's not running. */
    private Date profileStartTime = null;
    /** Future for when a automatic bid updates are scheduled */
    private ScheduledFuture<?> scheduledFuture = null;

    public TimeshifterAgent(AgentMessageSender messageHandler) {
        super(messageHandler);
    }

    @Override
    public void handleControlSpaceRegistration(ControlSpaceRegistration message) {
        if (message instanceof TimeShifterRegistration) {
            if (registration == null) {
                registration = (TimeShifterRegistration) message;
                if (!registration.getSupportedCommodities().contains(Commodity.ELECTRICITY)) {
                    LOGGER.error("PowerMatcher cannot support appliances which do not support electricity, removing agent");
                    messageSender.destroyAgent();
                }
            } else {
                LOGGER.error("Received multiple ControlSpaceRegistrations, ignoring...");
            }
        } else {
            LOGGER.error("Received unknown ContorlSpaceRegistration: " + message);
        }

    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleControlSpaceUpdate(ControlSpaceUpdate message) {
        if (message instanceof TimeShifterUpdate) {
            lastTimeshifterUpdate = (TimeShifterUpdate) message;
            List<SequentialProfile> timeShifterProfiles = lastTimeshifterUpdate.getTimeShifterProfiles();
            List<Element<CommodityUncertainMeasurables>> elements = new ArrayList<Element<CommodityUncertainMeasurables>>();
            for (SequentialProfile sp : timeShifterProfiles) {
                elements.addAll(sp.getCommodityForecast());
            }
            concatenatedCommodityForecast = new CommodityForecast(elements.toArray(new Element[elements.size()]));
            doBidUpdate();
        } else {
            LOGGER.error("Received unknown type of ControlSpaceUpdate: " + message);
        }
    }

    @Override
    public void handleAllocationStatusUpdate(AllocationStatusUpdate message) {
        switch (message.getStatus()) {
        case ACCEPTED:
            // Great! No action.
            break;
        case REJECTED:
            goToNoFlexiblityState();
            break;
        case PROCESSING:
            // Great! No action.
            break;
        case STARTED:
            // Start 'playing' the profile
            profileStartTime = message.getTimestamp();
            scheduleBidUpdates(RUNNING_PROFILE_BID_UPDATE_INTERVAL_MS);
            break;
        case FINISHED:
            // Great! Go to no-flexibility state
            goToNoFlexiblityState();
            break;
        }
        doBidUpdate();
    }

    private void goToNoFlexiblityState() {
        lastTimeshifterUpdate = null;
        profileStartTime = null;
        cancelScheduledBidUpdates();
    }

    @Override
    public void handleControlSpaceRevoke(ControlSpaceRevoke message) {
        goToNoFlexiblityState();
    }

    @Override
    protected Bid createBid() {
        MarketBasis marketBasis = getMarketBasis();
        if (marketBasis == null) {
            return null;
        }
        if (lastTimeshifterUpdate == null) {
            // No flexibility, must not run bid
            return new ArrayBid.Builder(marketBasis).demand(0).build();
        } else if (lastTimeshifterUpdate.getValidFrom().getTime() > context.currentTimeMillis()) {
            // Flexible period starts in the future
            // Schedule a bid update when the flexibility starts
            long delay = lastTimeshifterUpdate.getValidFrom().getTime() - context.currentTimeMillis();
            context.schedule(this, Measure.valueOf(delay, MS));
            return Bid.flatDemand(marketBasis, 0);
        } else if (profileStartTime != null) {
            // Appliance is currently executing program
            return constructBidForRunningProgram(marketBasis);
        } else {
            // Flexibility bid
            return constructFlexibleBid(marketBasis);
        }

    }

    /**
     * Construct a bid when the agent is in the flexible period
     *
     * @param marketBasis
     *            Current MarketBasis
     * @return Bid update
     */
    private Bid constructFlexibleBid(MarketBasis marketBasis) {
        // determine how far time has progressed in comparison to the start window (start after until start before)
        long startAfter = lastTimeshifterUpdate.getValidFrom().getTime();
        long endBefore = lastTimeshifterUpdate.getEndBefore().getTime();
        long startBefore = endBefore - concatenatedCommodityForecast.getTotalDuration().longValue(MS);
        long startWindow = startBefore - startAfter;
        double initialDemandWatt = getInitialDemand().doubleValue(SI.WATT);

        if (startWindow <= 0) {
            // It should already start, so send the must-run bid
            return Bid.flatDemand(marketBasis, initialDemandWatt);
        }

        long timeSinceAllowableStart = context.currentTimeMillis() - startAfter;
        double ratio = Math.pow(timeSinceAllowableStart / startWindow, EAGERNESS);

        if (initialDemandWatt < 0) {
            // if the initial demand is supply, the ratio flips
            ratio = 1 - ratio;
        } else if (initialDemandWatt == 0) {
            // upriceUpdated() expects the demand not to be zero
            initialDemandWatt = 0.1;
        }

        // calculate the step price
        double priceRange = marketBasis.getMaximumPrice() - marketBasis.getMinimumPrice()
                            - (marketBasis.getPriceIncrement() * 2);
        double stepPrice = priceRange * ratio + marketBasis.getMinimumPrice() + marketBasis.getPriceIncrement();

        if (scheduledFuture == null) {
            scheduleBidUpdates(startWindow / getMarketBasis().getPriceSteps());
        }

        // the bid depends on whether the initial demand is actually demand or is supply
        if (initialDemandWatt > 0) {
            return new PointBid.Builder(marketBasis).add(stepPrice, initialDemandWatt).add(stepPrice, 0).build();
        } else {
            return new PointBid.Builder(marketBasis).add(stepPrice, 0).add(stepPrice, initialDemandWatt).build();
        }
    }

    /**
     * Construct a bid when the agent is executing a profile. This method also goes to the no-flexibility-state if the
     * program has finished.
     *
     * @param marketBasis
     *            Current MarketBasis
     * @return Bid update
     */
    private Bid constructBidForRunningProgram(MarketBasis marketBasis) {
        Measurable<Duration> offset = Measure.valueOf(context.currentTimeMillis() - profileStartTime.getTime(), MS);
        if (offset.longValue(MS) >= concatenatedCommodityForecast.getTotalDuration().longValue(MS)) {
            // Program finished
            goToNoFlexiblityState();
            return Bid.flatDemand(marketBasis, 0);
        } else {
            // Program currently running
            Element<CommodityUncertainMeasurables> elementAtOffset = concatenatedCommodityForecast.getElementAtOffset(offset);
            Measurable<Power> demand = elementAtOffset.getValue().get(Commodity.ELECTRICITY).getMean();
            return Bid.flatDemand(marketBasis, demand.doubleValue(SI.WATT));
        }
    }

    private Measurable<Power> getInitialDemand() {
        return lastTimeshifterUpdate.getTimeShifterProfiles()
                                    .get(0)
                                    .getCommodityForecast()
                                    .get(0)
                                    .getValue()
                                    .get(Commodity.ELECTRICITY)
                                    .getMean();
    }

    @Override
    protected void handlePriceUpdate(Price newPrice) {
        // Do an allocation?
        if (lastTimeshifterUpdate != null && profileStartTime == null) {
            // We're in the flexibility period, the program hasn't started yet
            double demandForCurrentPrice = getLastBidUpdate().getBid().getDemandAt(newPrice);
            if (demandForCurrentPrice != 0) {
                // Let's start!
                final Date startTime = new Date(context.currentTimeMillis());
                Date sequentialProfielStartTime = startTime;
                List<SequentialProfileAllocation> seqAllocs = new ArrayList<SequentialProfileAllocation>(lastTimeshifterUpdate.getTimeShifterProfiles()
                                                                                                                              .size());
                for (SequentialProfile sp : lastTimeshifterUpdate.getTimeShifterProfiles()) {
                    seqAllocs.add(new SequentialProfileAllocation(sp.getId(), sequentialProfielStartTime));
                    sequentialProfielStartTime = TimeUtil.add(sequentialProfielStartTime, sp.getCommodityForecast()
                                                                                            .getTotalDuration());
                }
                TimeShifterAllocation allocation = new TimeShifterAllocation(lastTimeshifterUpdate,
                                                                             now(),
                                                                             false,
                                                                             seqAllocs);
                messageSender.sendMessage(allocation);
                // profileStartTime is set in the handleAllocationStatusUpdate method
            }
        }
    }

    @Override
    public void run() {
        doBidUpdate();
    }

    private void scheduleBidUpdates(long intervalMs) {
        cancelScheduledBidUpdates();
        Measure<Long, Duration> interval = Measure.valueOf(intervalMs, MS);
        scheduledFuture = context.scheduleAtFixedRate(this, interval, interval);
    }

    private void cancelScheduledBidUpdates() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
    }
}
