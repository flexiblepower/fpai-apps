package net.powermatcher.fpai.agents;

import static net.powermatcher.fpai.test.BidAnalyzer.assertFlatBidWithValue;

import java.util.Date;

import javax.measure.Measure;
import javax.measure.quantity.Power;
import javax.measure.quantity.VolumetricFlowRate;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import junit.framework.TestCase;
import net.powermatcher.api.data.MarketBasis;
import net.powermatcher.api.data.Price;
import net.powermatcher.api.messages.PriceUpdate;
import net.powermatcher.fpai.test.MockAgentSender;
import net.powermatcher.fpai.test.MockSession;
import net.powermatcher.mock.MockContext;

import org.flexiblepower.efi.uncontrolled.UncontrolledForecast;
import org.flexiblepower.efi.uncontrolled.UncontrolledMeasurement;
import org.flexiblepower.efi.uncontrolled.UncontrolledRegistration;
import org.flexiblepower.ral.values.CommodityForecast;
import org.flexiblepower.ral.values.CommodityMeasurables;
import org.flexiblepower.ral.values.CommoditySet;
import org.flexiblepower.ral.values.CommodityUncertainMeasurables;
import org.flexiblepower.ral.values.ConstraintListMap;
import org.flexiblepower.ral.values.UncertainMeasure;

public class UncontrolledAgentTest extends TestCase {

    private final MarketBasis marketBasis = new MarketBasis("electricity", "EUR", 100, 0, 99);
    private final MockSession session = new MockSession(marketBasis);
    private final MockContext context = new MockContext(System.currentTimeMillis());

    private MockAgentSender<UncontrolledAgent> agentSender;
    private UncontrolledAgent agent;

    @Override
    protected void setUp() throws Exception {
        agentSender = MockAgentSender.create(UncontrolledAgent.class);
        agent = agentSender.getAgent();
        agent.activate("agent-1", "matcher");
        agent.setContext(context);
        agent.connectToMatcher(session);
    }

    /**
     * Test: Agent has not received message but does receive price update
     *
     * Expected behavior: Agent does nothing and doesn't break
     */
    public void testNoRegistration() {
        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), 1));
        assertNull(agentSender.getLastMessage());
    }

    /**
     * Test: Agent has not received update message but does receive price update
     *
     * Expected behavior: Agent does nothing and doesn't break
     */
    public void testNoUpdate() {
        agentSender.handleMessage(new UncontrolledRegistration("resourceId",
                                                               new Date(),
                                                               Measure.zero(SI.SECOND),
                                                               CommoditySet.onlyElectricity,
                                                               ConstraintListMap.EMPTY));
        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), 1));
    }

    /**
     * Test: Agent receives a registration message of a device which does not support electricity
     *
     * Expected behavior: Agent disconnects
     */
    public void testNoElectricityRegistration() {
        agentSender.handleMessage(new UncontrolledRegistration("resourceId",
                                                               new Date(),
                                                               Measure.zero(SI.SECOND),
                                                               CommoditySet.onlyGas,
                                                               ConstraintListMap.EMPTY));
    }

    /**
     * Test: Agent receives an {@link UncontrolledMeasurement} without a value for electricity
     *
     * Expected behavior: Agent does not break and does not disconnect
     */
    public void testUncontrolledMeasurementWithoutElectricity() {
        agentSender.handleMessage(new UncontrolledRegistration("resourceId",
                                                               new Date(),
                                                               Measure.zero(SI.SECOND),
                                                               CommoditySet.create().addElectricity().addGas().build(),
                                                               ConstraintListMap.EMPTY));

        Date now = new Date();
        CommodityMeasurables commodityMeasurables = CommodityMeasurables.create()
                                                                        .gas(Measure.valueOf(10,
                                                                                             VolumetricFlowRate.UNIT))
                                                                        .build();
        agentSender.handleMessage(new UncontrolledMeasurement("resourceId", now, now, commodityMeasurables));
    }

    /**
     * Test: Agent receives an {@link UncontrolledForecast}
     *
     * Expected behavior: Agent ignores it; does not break and does not disconnect
     */
    public void testUncontrolledForecast() {
        agentSender.handleMessage(new UncontrolledRegistration("resourceId",
                                                               new Date(),
                                                               Measure.zero(SI.SECOND),
                                                               CommoditySet.create().addElectricity().addGas().build(),
                                                               ConstraintListMap.EMPTY));

        Date now = new Date();
        CommodityForecast forecast = CommodityForecast.create()
                                                      .duration(Measure.valueOf(10, NonSI.MINUTE))
                                                      .add(CommodityUncertainMeasurables.create()
                                                                                        .electricity(new UncertainMeasure<Power>(50,
                                                                                                                                 SI.WATT))
                                                                                        .build())
                                                      .build();
        agent.handleControlSpaceUpdate(new UncontrolledForecast("resourceId", now, now, forecast));
    }

    /**
     * Test: Agent receives an {@link UncontrolledMeasurement}
     *
     * Expected behavior: Agent publishes flat bid with the given demand
     */
    public void testLoads() {
        double[] demands = { 0, -100, 500, 200000, 9999999 };

        agentSender.handleMessage(new UncontrolledRegistration("resourceId",
                                                               new Date(),
                                                               Measure.zero(SI.SECOND),
                                                               CommoditySet.onlyElectricity,
                                                               ConstraintListMap.EMPTY));

        for (double demand : demands) {
            Date now = new Date();
            CommodityMeasurables commodityMeasurables = CommodityMeasurables.create()
                                                                            .electricity(Measure.valueOf(demand,
                                                                                                         SI.WATT))
                                                                            .gas(Measure.valueOf(10,
                                                                                                 VolumetricFlowRate.UNIT))
                                                                            .build();
            agentSender.handleMessage(new UncontrolledMeasurement("resourceId", now, now, commodityMeasurables));
            assertFlatBidWithValue(session.getLastBid().getBid(), Measure.valueOf(demand, SI.WATT));
        }

    }
}
