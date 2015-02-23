package net.powermatcher.fpai.agents;

import java.util.Collection;
import java.util.Collections;
import java.util.TreeSet;

import net.powermatcher.api.data.ArrayBid;
import net.powermatcher.api.data.Bid;
import net.powermatcher.api.data.MarketBasis;
import net.powermatcher.api.data.PointBid;
import net.powermatcher.api.data.Price;

/**
 * Bid storage and creation specifically for EFI Buffer devices. It holds more information than simply the PowerMatcher
 * bid, like the actuator and the RunningMode that belongs to each of the bid values.
 */
public class BufferBid {

    /**
     * An inner class describing elements of the bid. It has one actuator, the runningmode the pricefraction and the
     * demand.
     */
    protected static class BufferBidElement implements Comparable<BufferBidElement> {

        private final int actuatorId;
        private final int runningModeId;
        private final double demandWatt;

        public BufferBidElement(int actuatorId, int runningModeId, double demandWatt) {
            this.actuatorId = actuatorId;
            this.runningModeId = runningModeId;
            this.demandWatt = demandWatt;
        }

        public int getActuatorId() {
            return actuatorId;
        }

        public int getRunningModeId() {
            return runningModeId;
        }

        public double getDemandWatt() {
            return demandWatt;
        }

        @Override
        public int compareTo(BufferBidElement o) {
            // Sort from high demand to low demand
            return Double.compare(o.demandWatt, demandWatt);
        }

        @Override
        public String toString() {
            return "BufferBidElement [actuatorId=" + actuatorId
                   + ", runningModeId="
                   + runningModeId
                   + ", demandWatt="
                   + demandWatt
                   + "]";
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + actuatorId;
            long temp;
            temp = Double.doubleToLongBits(demandWatt);
            result = prime * result + (int) (temp ^ (temp >>> 32));
            result = prime * result + runningModeId;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            BufferBidElement other = (BufferBidElement) obj;
            if (actuatorId != other.actuatorId) {
                return false;
            }
            if (Double.doubleToLongBits(demandWatt) != Double.doubleToLongBits(other.demandWatt)) {
                return false;
            }
            if (runningModeId != other.runningModeId) {
                return false;
            }
            return true;
        }
    }

    private final TreeSet<BufferBidElement> elements;
    private final MarketBasis marketBasis;
    private BufferBidElement[] bufferBid;
    private final double priority;

    /**
     * Construct a BufferBid with a single {@link BufferBidElement}
     *
     * @param marketBasis
     *            The current {@link MarketBasis}
     * @param elements
     *            The {@link BufferBidElement} objects representing the current runningmodes
     * @param priority
     *            A double indicating the eagerness to consume or produce. A value >= 1 will result in a bid with
     *            maximum consumption (or minimal production), a value of 0 will result in a bid with maximum
     *            flexibility (the device doesn't care which runningmode is selected, a value <=-1 will result in a bid
     *            with minimal consumption (or maximal production)
     */
    public BufferBid(MarketBasis marketBasis, BufferBidElement element, double priority) {
        this(marketBasis, Collections.singleton(element), priority);
    }

    /**
     * Construct a BufferBid with a set of {@link BufferBidElement}s
     *
     * @param marketBasis
     *            The current {@link MarketBasis}
     * @param elements
     *            The {@link BufferBidElement} objects representing the current runningmodes
     * @param priority
     *            A double indicating the eagerness to consume or produce. A value >= 1 will result in a bid with
     *            maximum consumption (or minimal production), a value of 0 will result in a bid with maximum
     *            flexibility (the device doesn't care which runningmode is selected, a value <=-1 will result in a bid
     *            with minimal consumption (or maximal production)
     */
    public BufferBid(MarketBasis marketBasis, Collection<BufferBidElement> elements, double priority) {
        if (elements == null || elements.isEmpty()) {
            throw new IllegalStateException("Cannot construct an empty BufferBid");
        }
        this.elements = new TreeSet<BufferBidElement>();
        this.elements.addAll(elements);
        this.priority = Math.pow(priority, 3); // TODO is this a good value?
        this.marketBasis = marketBasis;
        constructBid();
    }

    private void constructBid() {
        Bid rawBid;
        double maxDemand = elements.first().demandWatt;
        double minDemand = elements.last().demandWatt;

        if (priority >= 1.0) {
            rawBid = Bid.flatDemand(marketBasis, maxDemand);
        } else if (priority <= -1.0) {
            rawBid = Bid.flatDemand(marketBasis, minDemand);
        } else { // priority > -1 && priority < 1
            // Define the flexible area
            double minPriceFraction, maxPriceFraction;
            if (priority == 0.0) {
                minPriceFraction = 0.0;
                maxPriceFraction = 1.0;
            } else if (priority > 0.0) {
                // 0.0 < priority < 1.0
                minPriceFraction = priority;
                maxPriceFraction = 1.0;
            } else {
                // -1.0 < priority < 0.0
                minPriceFraction = 0.0;
                maxPriceFraction = priority + 1;
            }
            // First make the ideal, continuous bid, that does not care about discrete running modes.
            rawBid = new PointBid.Builder(marketBasis).add(priceOf(minPriceFraction), maxDemand)
                                                      .add(priceOf(maxPriceFraction), minDemand)
                                                      .build();
        }

        // Now construct actual bid
        double[] rawDemand = rawBid.toArrayBid().getDemand();
        bufferBid = new BufferBidElement[rawDemand.length];
        for (int i = 0; i < rawDemand.length; i++) {
            bufferBid[i] = getClosest(rawDemand[i]);
        }
    }

    private BufferBidElement getClosest(double demandWatt) {
        BufferBidElement best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BufferBidElement e : elements) {
            double distance = Math.abs(demandWatt - e.demandWatt);
            if (best == null || distance < bestDistance) {
                best = e;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Returns the index that comes closest to the price
     *
     * @param priceFraction
     *            The fraction (between 0 and 1) on the price scale.
     * @return The index of the price step closest to the given price fraction.
     */
    private double priceOf(double priceFraction) {
        return marketBasis.getMinimumPrice() + priceFraction
               * (marketBasis.getMaximumPrice() - marketBasis.getMinimumPrice());
    }

    public Bid toBid() {
        double[] demand = new double[bufferBid.length];
        for (int i = 0; i < bufferBid.length; i++) {
            demand[i] = bufferBid[i].demandWatt;
        }
        return new ArrayBid(marketBasis, demand);
    }

    /**
     * Given a PriceInfo object, the BufferBidElement is returned that expresses which actuator should go into which
     * RunningMode.
     *
     * @param price
     * @return
     */
    public BufferBidElement runningModeForPrice(Price price) {
        return bufferBid[price.toPriceStep().getPriceStep()];
    }

    @Override
    public String toString() {
        return "BufferBid [elements=" + elements + "]";
    }

}
