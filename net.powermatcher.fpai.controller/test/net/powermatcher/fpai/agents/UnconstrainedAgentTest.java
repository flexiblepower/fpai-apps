package net.powermatcher.fpai.agents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import javax.measure.Measure;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import junit.framework.Assert;
import junit.framework.TestCase;
import net.powermatcher.api.data.Bid;
import net.powermatcher.api.data.MarketBasis;
import net.powermatcher.api.data.Price;
import net.powermatcher.api.messages.PriceUpdate;
import net.powermatcher.fpai.test.BidAnalyzer;
import net.powermatcher.fpai.test.MockAgentSender;
import net.powermatcher.fpai.test.MockSession;
import net.powermatcher.mock.MockContext;

import org.flexiblepower.efi.unconstrained.RunningModeBehaviour;
import org.flexiblepower.efi.unconstrained.RunningModeSelector;
import org.flexiblepower.efi.unconstrained.UnconstrainedAllocation;
import org.flexiblepower.efi.unconstrained.UnconstrainedRegistration;
import org.flexiblepower.efi.unconstrained.UnconstrainedStateUpdate;
import org.flexiblepower.efi.unconstrained.UnconstrainedSystemDescription;
import org.flexiblepower.efi.util.RunningMode;
import org.flexiblepower.efi.util.Timer;
import org.flexiblepower.efi.util.TimerUpdate;
import org.flexiblepower.efi.util.Transition;
import org.flexiblepower.ral.values.CommodityMeasurables;
import org.flexiblepower.ral.values.CommoditySet;

public class UnconstrainedAgentTest extends TestCase {

    private final static String RESOURCE_ID = "resourceId3";

    private final MarketBasis marketBasis = new MarketBasis("electricity", "EUR", 100, 0, 99);
    private final MockSession session = new MockSession(marketBasis);
    private final MockContext context = new MockContext(System.currentTimeMillis());

    private UnconstrainedRegistration unconstrainedRegistration() {
        return new UnconstrainedRegistration(RESOURCE_ID,
                                             context.currentTime(),
                                             Measure.zero(SI.SECOND),
                                             CommoditySet.onlyElectricity);
    }

    private UnconstrainedSystemDescription systemDescription(UnconstrainedRegistration registration) {
        Timer minOnTimer = new Timer(0, "minOnTimer", Measure.valueOf(10, SI.SECOND));
        Timer minOffTimer = new Timer(1, "minOffTimer", Measure.valueOf(10, SI.SECOND));

        RunningModeBehaviour offRM = new RunningModeBehaviour(CommodityMeasurables.electricity(Measure.valueOf(0,
                                                                                                               SI.WATT)),
                                                              Measure.valueOf(0, NonSI.EUR_PER_HOUR));

        RunningMode<RunningModeBehaviour> off = new RunningMode<RunningModeBehaviour>(0,
                                                                                      "off",
                                                                                      offRM,
                                                                                      Collections.singleton(Transition.create(1)
                                                                                                                      .starts(minOnTimer)
                                                                                                                      .isBlockedBy(minOffTimer)
                                                                                                                      .build()));

        RunningModeBehaviour onRM = new RunningModeBehaviour(CommodityMeasurables.electricity(Measure.valueOf(1000,
                                                                                                              SI.WATT)),
                                                             Measure.valueOf(0, NonSI.EUR_PER_HOUR));

        RunningMode<RunningModeBehaviour> on = new RunningMode<RunningModeBehaviour>(1,
                                                                                     "on",
                                                                                     onRM,
                                                                                     Collections.singleton(Transition.create(0)
                                                                                                                     .starts(minOffTimer)
                                                                                                                     .isBlockedBy(minOnTimer)
                                                                                                                     .build()));
        Collection<RunningMode<RunningModeBehaviour>> runningModes = new ArrayList<RunningMode<RunningModeBehaviour>>();
        runningModes.add(on);
        runningModes.add(off);
        UnconstrainedSystemDescription usd = new UnconstrainedSystemDescription(RESOURCE_ID,
                                                                                context.currentTime(),
                                                                                context.currentTime(),
                                                                                runningModes);
        return usd;
    }

    private UnconstrainedSystemDescription secondSystemDescription(UnconstrainedRegistration registration) {
        Timer minOnTimer = new Timer(2, "minOnTimer", Measure.valueOf(10, SI.SECOND));
        Timer minOffTimer = new Timer(3, "minOffTimer", Measure.valueOf(10, SI.SECOND));

        RunningModeBehaviour offRM = new RunningModeBehaviour(CommodityMeasurables.electricity(Measure.valueOf(0,
                                                                                                               SI.WATT)),
                                                              Measure.valueOf(0, NonSI.EUR_PER_HOUR));

        RunningMode<RunningModeBehaviour> off = new RunningMode<RunningModeBehaviour>(2,
                                                                                      "off",
                                                                                      offRM,
                                                                                      Collections.singleton(Transition.create(3)
                                                                                                                      .starts(minOnTimer)
                                                                                                                      .isBlockedBy(minOffTimer)
                                                                                                                      .build()));

        RunningModeBehaviour onRM = new RunningModeBehaviour(CommodityMeasurables.electricity(Measure.valueOf(1000,
                                                                                                              SI.WATT)),
                                                             Measure.valueOf(0, NonSI.EUR_PER_HOUR));

        RunningMode<RunningModeBehaviour> on = new RunningMode<RunningModeBehaviour>(3,
                                                                                     "on",
                                                                                     onRM,
                                                                                     Collections.singleton(Transition.create(2)
                                                                                                                     .starts(minOffTimer)
                                                                                                                     .isBlockedBy(minOnTimer)
                                                                                                                     .build()));
        Collection<RunningMode<RunningModeBehaviour>> runningModes = new ArrayList<RunningMode<RunningModeBehaviour>>();
        runningModes.add(on);
        runningModes.add(off);
        return new UnconstrainedSystemDescription(RESOURCE_ID,
                                                  context.currentTime(),
                                                  context.currentTime(),
                                                  runningModes);
    }

    private MockAgentSender<UnconstrainedAgent> agentSender;
    private UnconstrainedAgent agent;

    @Override
    protected void setUp() throws Exception {
        agentSender = MockAgentSender.create(UnconstrainedAgent.class, "agent-1", "matcher");
        agent = agentSender.getAgent();
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
     * Test: Agent receives a registration, then an updated price (nothing else).
     *
     * Agent should not unregister and should not send an allocation (and should not crash).
     */
    public void testNoSystemDescription() {
        agent.handleControlSpaceRegistration(unconstrainedRegistration());
        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), 1));
        assertNull(agentSender.getLastMessage());
    }

    /**
     * Test: Agent has two runningmodes, but the other is currently blocked by a timer
     *
     * Expected behavior: Agent creates a bid without flexibility.
     */
    public void testTimerBlocking() {
        UnconstrainedRegistration registration = unconstrainedRegistration();
        agentSender.handleMessage(registration);

        UnconstrainedSystemDescription usd = systemDescription(registration);
        agentSender.handleMessage(usd);

        TimerUpdate minOnTimer = new TimerUpdate(0, new Date(context.currentTimeMillis() + 5000)); // blocking
        UnconstrainedStateUpdate usu = new UnconstrainedStateUpdate(RESOURCE_ID,
                                                                    context.currentTime(),
                                                                    context.currentTime(),
                                                                    1,
                                                                    Collections.singleton(minOnTimer));

        agentSender.handleMessage(usu);

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), 1));

        // Bid should not have flexibility
        Bid bid = session.getLastBid().getBid();
        BidAnalyzer.assertFlatBidWithValue(bid, Measure.valueOf(1000, SI.WATT));
    }

    /**
     * Test: Agent has two runningmodes, but they are not connected through a transition
     *
     * Expected behavior: Agent creates a bid without flexiblity
     */
    public void testNoTransition() {
        agentSender.handleMessage(new UnconstrainedRegistration("noreachable",
                                                                context.currentTime(),
                                                                Measure.valueOf(0d, SI.SECOND),
                                                                CommoditySet.onlyElectricity));

        Collection<RunningMode<RunningModeBehaviour>> runningModes = new ArrayList<RunningMode<RunningModeBehaviour>>();
        runningModes.add(new RunningMode<RunningModeBehaviour>(1,
                                                               "off",
                                                               new RunningModeBehaviour(CommodityMeasurables.electricity(Measure.valueOf(0,
                                                                                                                                         SI.WATT)),
                                                                                        Measure.valueOf(0,
                                                                                                        NonSI.EUR_PER_HOUR)),
                                                               Collections.<Transition> emptySet()));

        runningModes.add(new RunningMode<RunningModeBehaviour>(2,
                                                               "on",
                                                               new RunningModeBehaviour(CommodityMeasurables.electricity(Measure.valueOf(0,
                                                                                                                                         SI.WATT)),
                                                                                        Measure.valueOf(0,
                                                                                                        NonSI.EUR_PER_HOUR)),
                                                               Collections.<Transition> singleton(new Transition(1,
                                                                                                                 Collections.<Timer> emptySet(),
                                                                                                                 Collections.<Timer> emptySet(),
                                                                                                                 Measure.valueOf(0,
                                                                                                                                 NonSI.EUR),
                                                                                                                 Measure.valueOf(0d,
                                                                                                                                 SI.SECOND)))));
        agentSender.handleMessage(new UnconstrainedSystemDescription("noreachable",
                                                                     context.currentTime(),
                                                                     context.currentTime(),
                                                                     runningModes));
        // Current Running Mode is 1 (off).
        agentSender.handleMessage(new UnconstrainedStateUpdate("noreachable",
                                                               context.currentTime(),
                                                               context.currentTime(),
                                                               1,
                                                               Collections.<TimerUpdate> emptySet()));

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), 1));

        // Bid should not have flexibility and should be 0 W.
        Bid bid = session.getLastBid().getBid();
        BidAnalyzer.assertFlatBidWithValue(bid, Measure.valueOf(0d, SI.WATT));
    }

    /**
     * Test: Agent has a definition of a blocking timer, but the timer is not set.
     *
     * Expected behavior: Device may go to either state, so a step bid is constructed.
     */
    public void testBlockingTimerNotSet() {
        UnconstrainedRegistration registration = unconstrainedRegistration();
        agentSender.handleMessage(registration);

        UnconstrainedSystemDescription usd = systemDescription(registration);
        agentSender.handleMessage(usd);

        agentSender.handleMessage(new UnconstrainedStateUpdate("",
                                                               context.currentTime(),
                                                               context.currentTime(),
                                                               1,
                                                               Collections.<TimerUpdate> emptySet()));

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), 1));

        // Bid should have flexibility
        Bid bid2 = session.getLastBid().getBid();
        System.out.println("bid with no sys update" + bid2);
        BidAnalyzer.assertStepBid(bid2);
    }

    /**
     * Test: Blocking Timer is finished so the device should be flexible.
     *
     * Outcome should be that the agent makes a bid with two price points, one for staying in the state or make a
     * transition.
     */
    public void testBlockingTimerFinished() {
        UnconstrainedRegistration registration = unconstrainedRegistration();
        agentSender.handleMessage(registration);

        UnconstrainedSystemDescription usd = systemDescription(registration);
        agentSender.handleMessage(usd);
        Date justBefore = new Date(context.currentTimeMillis() - 1000);
        System.out.println("just before: " + justBefore);
        Date now = context.currentTime();
        System.out.println(now);
        TimerUpdate expiredMinOnTimer = new TimerUpdate(0, justBefore); // blocking

        UnconstrainedStateUpdate usu2 = new UnconstrainedStateUpdate(RESOURCE_ID,
                                                                     now,
                                                                     now,
                                                                     1,
                                                                     Collections.singleton(expiredMinOnTimer));
        agentSender.handleMessage(usu2);
        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), 1));

        // Bid should have flexibility
        Bid bid2 = session.getLastBid().getBid();
        System.out.println(bid2);
        BidAnalyzer.assertStepBid(bid2);
    }

    /**
     * Test: Test BidResponse when device is in must-run.
     *
     * Outcome should be that the device is going to the must-run running mode.
     */
    public void testBidResponseMustRun() {
        UnconstrainedRegistration registration = unconstrainedRegistration();
        agentSender.handleMessage(registration);

        UnconstrainedSystemDescription usd = systemDescription(registration);
        agentSender.handleMessage(usd);

        TimerUpdate minOnTimer = new TimerUpdate(0, new Date(context.currentTimeMillis() + 5000)); // blocking

        UnconstrainedStateUpdate usu = new UnconstrainedStateUpdate(RESOURCE_ID,
                                                                    context.currentTime(),
                                                                    context.currentTime(),
                                                                    1,
                                                                    Collections.singleton(minOnTimer));

        agentSender.handleMessage(usu);

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), 1));

        Assert.assertEquals(Collections.<RunningModeSelector> singleton(new RunningModeSelector(1,
                                                                                                context.currentTime())),
                            (((UnconstrainedAllocation) (agentSender.getLastMessage())).getRunningModeSelectors()));

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMinimumPrice()), 1));

        Assert.assertEquals(Collections.<RunningModeSelector> singleton(new RunningModeSelector(1,
                                                                                                context.currentTime())),
                            (((UnconstrainedAllocation) (agentSender.getLastMessage())).getRunningModeSelectors()));

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMaximumPrice()), 1));

        Assert.assertEquals(Collections.<RunningModeSelector> singleton(new RunningModeSelector(1,
                                                                                                context.currentTime())),
                            (((UnconstrainedAllocation) (agentSender.getLastMessage())).getRunningModeSelectors()));
    }

    /**
     * Test: Test BidResponse when device is flexible.
     *
     * Outcome should be that the device is going to be off at maximum price and on at minimum price.
     */
    public void testBidResponseFlexible() {
        UnconstrainedRegistration registration = unconstrainedRegistration();
        agentSender.handleMessage(registration);

        UnconstrainedSystemDescription usd = systemDescription(registration);
        agentSender.handleMessage(usd);

        TimerUpdate minOnTimer = new TimerUpdate(0, new Date(context.currentTimeMillis() - 50)); // blocking

        UnconstrainedStateUpdate usu = new UnconstrainedStateUpdate(RESOURCE_ID,
                                                                    context.currentTime(),
                                                                    context.currentTime(),
                                                                    1,
                                                                    Collections.singleton(minOnTimer));

        agentSender.handleMessage(usu);

        // Upon minimum price it must consume.
        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMinimumPrice()), 1));

        Assert.assertEquals(Collections.<RunningModeSelector> singleton(new RunningModeSelector(1,
                                                                                                context.currentTime())),
                            (((UnconstrainedAllocation) (agentSender.getLastMessage())).getRunningModeSelectors()));

        // Upon maximum price it must go off.
        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMaximumPrice()), 1));

        Assert.assertEquals(Collections.<RunningModeSelector> singleton(new RunningModeSelector(0,
                                                                                                context.currentTime())),
                            (((UnconstrainedAllocation) (agentSender.getLastMessage())).getRunningModeSelectors()));

    }

    /**
     * Test: not implemented yet...
     */
    public void testNewSystemDescription() {
        UnconstrainedRegistration registration = unconstrainedRegistration();
        agentSender.handleMessage(registration);

        UnconstrainedSystemDescription usd = systemDescription(registration);
        agentSender.handleMessage(usd);

        TimerUpdate minOnTimer = new TimerUpdate(0, new Date(context.currentTimeMillis() + 5000)); // blocking

        UnconstrainedStateUpdate usu = new UnconstrainedStateUpdate(RESOURCE_ID,
                                                                    context.currentTime(),
                                                                    context.currentTime(),
                                                                    1,
                                                                    Collections.singleton(minOnTimer));

        agentSender.handleMessage(usu);

        agentSender.handleMessage(secondSystemDescription(registration));

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMinimumPrice()), 1));

        agentSender.handleMessage(new UnconstrainedStateUpdate(RESOURCE_ID,
                                                               context.currentTime(),
                                                               context.currentTime(),
                                                               2,
                                                               Collections.<TimerUpdate> emptySet()));
    }
}
