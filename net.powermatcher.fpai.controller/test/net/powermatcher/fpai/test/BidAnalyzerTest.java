package net.powermatcher.fpai.test;

import static javax.measure.unit.SI.WATT;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Power;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import net.powermatcher.api.data.Bid;
import net.powermatcher.api.data.MarketBasis;
import net.powermatcher.api.data.PointBid;
import net.powermatcher.api.data.Price;
import net.powermatcher.api.data.PricePoint;
import net.powermatcher.api.data.PriceStep;

public class BidAnalyzerTest extends TestCase {
    private static final MarketBasis MARKET_BASIS = new MarketBasis("Electricity", "EUR", 101, 0, 50);

    private final Measurable<Power> power1 = Measure.valueOf(10, WATT);
    private final Measurable<Power> power2 = Measure.valueOf(0, WATT);
    private final Measurable<Power> power3 = Measure.valueOf(-10, WATT);

    /** test for all prices apart from the lowest and the highest */
    public void testBidAnalyzer() {
        for (int priceIndex = 1; priceIndex < MARKET_BASIS.getPriceSteps() - 1; priceIndex++) {
            Price price = new PriceStep(MARKET_BASIS, priceIndex).toPrice();

            Bid bid = createBid(power1, power2, price);

            BidAnalyzer.assertStepBid(bid, null, null, null);
            BidAnalyzer.assertStepBid(bid, power1, power2, price.getPriceValue());

            BidAnalyzer.assertStepBid(bid, power1, null, null);
            BidAnalyzer.assertStepBid(bid, null, power2, null);
            BidAnalyzer.assertStepBid(bid, null, null, price.getPriceValue());

            BidAnalyzer.assertStepBid(bid, power1, power2, null);
            BidAnalyzer.assertStepBid(bid, power1, null, price.getPriceValue());
            BidAnalyzer.assertStepBid(bid, null, power2, price.getPriceValue());
        }
    }

    /** test that with a must-run bid, the assertion fails */
    public void testAssertStepBidMinimumPrice() {
        try {
            double price = new PriceStep(MARKET_BASIS, 0).toPrice().getPriceValue();

            Bid bid = createBid(power1, power2, new PriceStep(MARKET_BASIS, 0).toPrice());
            BidAnalyzer.assertStepBid(bid, power1, power2, price);
            fail("Expected Exception");
        } catch (AssertionFailedError e) {
        }
    }

    /** test that with a must-run bid, the assertion fails */
    public void testAssertStepBidMaximumPrice() {
        try {
            Price price = new PriceStep(MARKET_BASIS, MARKET_BASIS.getPriceSteps() - 1).toPrice();

            Bid bid = createBid(power1, power2, price);
            BidAnalyzer.assertStepBid(bid, power1, power2, price.getPriceValue());

            fail("Expected Exception");
        } catch (AssertionFailedError e) {
        }
    }

    /** test that with a must-run bid, the assertion fails */
    public void testAssertStepMultiStep() {
        try {
            Price price1 = new PriceStep(MARKET_BASIS, MARKET_BASIS.getPriceSteps() / 4).toPrice();
            Price price2 = new PriceStep(MARKET_BASIS, MARKET_BASIS.getPriceSteps() / 4 * 3).toPrice();

            PricePoint pricePoint1 = new PricePoint(price1, power1.doubleValue(WATT));
            PricePoint pricePoint2 = new PricePoint(price1, power2.doubleValue(WATT));
            PricePoint pricePoint3 = new PricePoint(price2, power2.doubleValue(WATT));
            PricePoint pricePoint4 = new PricePoint(price2, power3.doubleValue(WATT));

            Bid bid = new PointBid(MARKET_BASIS, pricePoint1, pricePoint2, pricePoint3, pricePoint4);
            BidAnalyzer.assertStepBid(bid);

            fail("Expected Exception");
        } catch (AssertionFailedError e) {
        }
    }

    private Bid createBid(Measurable<Power> power1, Measurable<Power> power2, Price price) {
        PricePoint pricePoint1 = new PricePoint(price, power1.doubleValue(WATT));
        PricePoint pricePoint2 = new PricePoint(price, power2.doubleValue(WATT));

        return new PointBid(MARKET_BASIS, pricePoint1, pricePoint2);
    }
}
