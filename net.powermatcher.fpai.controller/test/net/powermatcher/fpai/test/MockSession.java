package net.powermatcher.fpai.test;

import net.powermatcher.api.Session;
import net.powermatcher.api.data.MarketBasis;
import net.powermatcher.api.messages.BidUpdate;
import net.powermatcher.api.messages.PriceUpdate;

public class MockSession implements Session {

    private final MarketBasis marketBasis;
    private BidUpdate lastBid;

    public MockSession(MarketBasis marketBasis) {
        this.marketBasis = marketBasis;
    }

    @Override
    public void updateBid(BidUpdate newBid) {
        lastBid = newBid;
    }

    @Override
    public String getAgentId() {
        return "agent-id";
    }

    @Override
    public String getMatcherId() {
        return "matcher-id";
    }

    @Override
    public String getClusterId() {
        return "cluster-id";
    }

    @Override
    public String getSessionId() {
        return "session-id";
    }

    @Override
    public MarketBasis getMarketBasis() {
        return marketBasis;
    }

    @Override
    public void setMarketBasis(MarketBasis marketBasis) {
    }

    @Override
    public void updatePrice(PriceUpdate newPrice) {
    }

    @Override
    public void disconnect() {
        lastBid = null;
    }

    public void reset() {
        lastBid = null;
    }

    public BidUpdate getLastBid() {
        return lastBid;
    }

}
