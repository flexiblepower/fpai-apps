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
 * This class helps the UnconstrainedAgent to make its PowerMatcher bids. The bid forming strategy is derived from the
 * bid forming of the Buffer agent.
 */
public class UnconstrainedBid {
    /**
     * An inner class describing elements of the bid. It has the RunningMode and the electrical power demand value.
     */
    protected static class UnconstrainedBidElement implements Comparable<UnconstrainedBidElement> {

        private final int runningModeId;
        private final double demandWatt;

        public UnconstrainedBidElement(int runningModeId, double demandWatt) {
            this.runningModeId = runningModeId;
            this.demandWatt = demandWatt;
        }

        public int getRunningModeId() {
            return runningModeId;
        }

        public double getDemandWatt() {
            return demandWatt;
        }

        @Override
        public int compareTo(UnconstrainedBidElement o) {
            // Sort from high demand to low demand
            return Double.compare(o.demandWatt, demandWatt);
        }

        @Override
        public String toString() {
            return "BufferBidElement [runningModeId=" + runningModeId + ", demandWatt=" + demandWatt + "]";
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
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
            UnconstrainedBidElement other = (UnconstrainedBidElement) obj;
            if (Double.doubleToLongBits(demandWatt) != Double.doubleToLongBits(other.demandWatt)) {
                return false;
            }
            if (runningModeId != other.runningModeId) {
                return false;
            }
            return true;
        }
    }

    private final MarketBasis marketBasis;
    private UnconstrainedBidElement[] newBid;
    private final TreeSet<UnconstrainedBidElement> elements;

    /**
     * Creates a simple UnconstrainedBid if you provide one running mode and demand value.
     *
     * @param element
     *            The single point that characterizes the bid curve.
     * @param marketBasis
     *            The market basis for this bid.
     */
    public UnconstrainedBid(UnconstrainedBidElement element, MarketBasis marketBasis) {
        this(Collections.<UnconstrainedBidElement> singleton(element), marketBasis);
    }

    /**
     * Creates a simple UnconstrainedBid from a collection of bid elements and the market basis.
     *
     * @param elements
     *            The collection of points that characterize the bid curve.
     * @param marketBasis
     *            The MarketBasis to form bids for.
     */
    public UnconstrainedBid(Collection<UnconstrainedBidElement> elements, MarketBasis marketBasis) {
        if (elements == null || elements.isEmpty()) {
            throw new IllegalStateException("Cannot construct an empty UnconstrainedBid");
        }
        this.elements = new TreeSet<UnconstrainedBidElement>();
        this.elements.addAll(elements);
        this.marketBasis = marketBasis;
        constructBid();
    }

    /**
     * Prepares the Power Matcher bid.
     */
    private void constructBid() {
        Bid rawBid;
        double maxDemand = elements.first().demandWatt;
        // Minimum demand is also Max Production.
        double minDemand = elements.last().demandWatt;

        double minPriority = 0.0;
        double maxPriority = 1.0;

        // First make the ideal, continuous bid, that does not care about discrete running modes.
        rawBid = new PointBid.Builder(marketBasis).add(priceOf(minPriority), maxDemand)
                                                  .add(priceOf(maxPriority), minDemand)
                                                  .build();

        // Now construct actual bid
        double[] rawDemand = rawBid.toArrayBid().getDemand();
        newBid = new UnconstrainedBidElement[rawDemand.length];
        for (int i = 0; i < rawDemand.length; i++) {
            newBid[i] = getClosest(rawDemand[i]);
        }
    }

    /**
     * Returns the bid element closest to the demand value given as input.
     *
     * @param demandWatt
     *            The demand in Watt.
     * @return The Bid element (Running Mode and demand) that comes closest to the input.
     */
    private UnconstrainedBidElement getClosest(double demandWatt) {
        UnconstrainedBidElement best = null;
        double bestDistance = Double.MAX_VALUE;
        for (UnconstrainedBidElement e : elements) {
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

    /**
     * Given a PriceInfo object, the UnconstrainedBidElement is returned that expresses which RunningMode the
     * Unconstrained device should go to.
     *
     * @param price
     *            The PowerMatcher Price object.
     * @return the BidElement with RunningMode and Price.
     */
    public UnconstrainedBidElement runningModeForPrice(Price price) {
        return newBid[price.toPriceStep().getPriceStep()];
    }

    /**
     * Produces the Bid object on the basis of the prepared info.
     *
     * @return The Bid object.
     */
    public Bid toBid() {
        double[] demand = new double[newBid.length];
        for (int i = 0; i < newBid.length; i++) {
            demand[i] = newBid[i].demandWatt;
        }
        return new ArrayBid(marketBasis, demand);
    }

    @Override
    public String toString() {
        return "UnconstrainedBid [elements=" + elements + "]";
    }
}
