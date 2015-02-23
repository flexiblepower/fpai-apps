package net.powermatcher.fpai.agents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.measure.Measure;
import javax.measure.unit.SI;

import junit.framework.Assert;
import junit.framework.TestCase;
import net.powermatcher.api.data.Bid;
import net.powermatcher.api.data.MarketBasis;
import net.powermatcher.api.data.Price;
import net.powermatcher.fpai.agents.BufferBid.BufferBidElement;
import net.powermatcher.fpai.test.BidAnalyzer;

public class BufferBidTest extends TestCase {

    private static MarketBasis marketBasis = new MarketBasis("electricity", "EUR", 10, 0.0, 0.9);

    public void testTwoRunningModesBufferHalfFull() {

        BufferBidElement off = new BufferBidElement(1, 1, 0);
        BufferBidElement on = new BufferBidElement(1, 2, 1000);

        BufferBid bb = new BufferBid(marketBasis, Arrays.asList(new BufferBidElement[] { off, on }), 0.0);

        Bid bid = bb.toBid();
        System.out.println(bid);

        Assert.assertEquals(on, bb.runningModeForPrice(new Price(marketBasis, 0.0)));
        Assert.assertEquals(on, bb.runningModeForPrice(new Price(marketBasis, 0.4)));
        Assert.assertEquals(off, bb.runningModeForPrice(new Price(marketBasis, 0.5)));
        Assert.assertEquals(off, bb.runningModeForPrice(new Price(marketBasis, 0.9)));
    }

    public void testConsumingAndProducingRunningModes() {

        BufferBidElement off = new BufferBidElement(1, 1, 0);

        BufferBidElement consuming = new BufferBidElement(1, 2, 1000);

        BufferBidElement producing = new BufferBidElement(1, 3, -1000);

        BufferBid bb = new BufferBid(marketBasis,
                                     Arrays.asList(new BufferBidElement[] { off, consuming, producing }),
                                     0.0);

        Bid bid = bb.toBid();
        System.out.println(bid);

        Assert.assertEquals(consuming, bb.runningModeForPrice(new Price(marketBasis, 0.0)));
        Assert.assertEquals(off, bb.runningModeForPrice(new Price(marketBasis, 0.5)));
        Assert.assertEquals(producing, bb.runningModeForPrice(new Price(marketBasis, 0.9)));

    }

    public void testTwoRunningModesBufferEmpty() {

        BufferBidElement off = new BufferBidElement(1, 1, 0);

        BufferBidElement on = new BufferBidElement(1, 2, 1000);

        BufferBid bb = new BufferBid(marketBasis, Arrays.asList(new BufferBidElement[] { off, on }), 1.0);

        Bid bid = bb.toBid();
        System.out.println(bid);

        BidAnalyzer.assertFlatBidWithValue(bid, Measure.valueOf(1000, SI.WATT));

        Assert.assertEquals(on, bb.runningModeForPrice(new Price(marketBasis, 0.9)));
        Assert.assertEquals(on, bb.runningModeForPrice(new Price(marketBasis, 0.0)));
    }

    public void testTwoRunningModesBufferFull() {

        BufferBidElement off = new BufferBidElement(1, 1, 0);

        BufferBidElement on = new BufferBidElement(1, 2, 1000);

        BufferBid bb = new BufferBid(marketBasis, Arrays.asList(new BufferBidElement[] { off, on }), -1.0);

        Bid bid = bb.toBid();
        System.out.println(bid);

        BidAnalyzer.assertFlatBidWithValue(bid, Measure.valueOf(0, SI.WATT));

        Assert.assertEquals(off, bb.runningModeForPrice(new Price(marketBasis, 0.9)));
        Assert.assertEquals(off, bb.runningModeForPrice(new Price(marketBasis, 0.0)));
    }

    public void testThreeRunningModes() {

        BufferBidElement off = new BufferBidElement(1, 1, 0);

        BufferBidElement half = new BufferBidElement(1, 2, 500);

        BufferBidElement full = new BufferBidElement(1, 3, 1000);

        // SoC = 0
        BufferBid bb = new BufferBid(marketBasis, Arrays.asList(new BufferBidElement[] { off, half, full }), 1.0);

        Bid bid = bb.toBid();
        System.out.println(bid);

        BidAnalyzer.assertDemandAtLeast(bid, Measure.valueOf(500, SI.WATT));

        // SoC = 0.5
        bb = new BufferBid(marketBasis, Arrays.asList(new BufferBidElement[] { off, half, full }), 0.0);

        bid = bb.toBid();
        System.out.println(bid);

        BidAnalyzer.assertNonFlatBid(bid);

        // SoC = 1
        bb = new BufferBid(marketBasis, Arrays.asList(new BufferBidElement[] { off, half, full }), -1.0);

        bid = bb.toBid();
        System.out.println(bid);

        BidAnalyzer.assertFlatBidWithValue(bid, Measure.valueOf(0, SI.WATT));
    }

    public void testOneRunningMode() {
        BufferBidElement on = new BufferBidElement(1, 2, 1000);

        BufferBid bb = new BufferBid(marketBasis, on, 0.0);

        Bid bid = bb.toBid();
        System.out.println(bid);

        Assert.assertEquals(on, bb.runningModeForPrice(new Price(marketBasis, 0.0)));
        Assert.assertEquals(on, bb.runningModeForPrice(new Price(marketBasis, 0.4)));
        BidAnalyzer.assertFlatBidWithValue(bid, Measure.valueOf(1000, SI.WATT));
    }

    public void testPrintPriority() {
        List<BufferBidElement> l = new ArrayList<BufferBidElement>();
        for (int i = 0; i <= 200; i++) {
            l.add(new BufferBidElement(1, i, i * 10 - 1000));
        }
        for (double priority = -1.1; priority <= 1.1; priority += 0.1) {
            System.out.println(priority + ": " + new BufferBid(marketBasis, l, priority).toBid());
        }
    }
}
