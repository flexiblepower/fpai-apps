package net.powermatcher.fpai.agents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.measure.Measure;
import javax.measure.quantity.Temperature;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import junit.framework.Assert;
import junit.framework.TestCase;
import net.powermatcher.api.data.Bid;
import net.powermatcher.api.data.MarketBasis;
import net.powermatcher.api.data.Price;
import net.powermatcher.api.messages.BidUpdate;
import net.powermatcher.api.messages.PriceUpdate;
import net.powermatcher.fpai.test.BidAnalyzer;
import net.powermatcher.fpai.test.MockAgentTracker;
import net.powermatcher.fpai.test.MockConnection;
import net.powermatcher.fpai.test.MockSession;
import net.powermatcher.mock.MockContext;

import org.flexiblepower.efi.buffer.Actuator;
import org.flexiblepower.efi.buffer.ActuatorBehaviour;
import org.flexiblepower.efi.buffer.ActuatorUpdate;
import org.flexiblepower.efi.buffer.BufferAllocation;
import org.flexiblepower.efi.buffer.BufferRegistration;
import org.flexiblepower.efi.buffer.BufferStateUpdate;
import org.flexiblepower.efi.buffer.BufferSystemDescription;
import org.flexiblepower.efi.buffer.BufferTargetProfileUpdate;
import org.flexiblepower.efi.buffer.LeakageRate;
import org.flexiblepower.efi.buffer.RunningModeBehaviour;
import org.flexiblepower.efi.util.FillLevelFunction;
import org.flexiblepower.efi.util.RunningMode;
import org.flexiblepower.efi.util.Timer;
import org.flexiblepower.efi.util.TimerUpdate;
import org.flexiblepower.efi.util.Transition;
import org.flexiblepower.ral.values.CommodityMeasurables;
import org.flexiblepower.ral.values.CommoditySet;
import org.flexiblepower.ral.values.Constraint;
import org.flexiblepower.ral.values.ConstraintProfile;

/**
 * Test suite for BufferAgent
 *
 * TODO: Currently it only tests a single actuator, should be more!
 *
 */
public class BufferAgentTest extends TestCase {

    private static final double NOMINAL_POWER_ON = 1000d;
    private static final double ONE_HUNDREDTH = 0.01;
    private static final double FILL_RATE_ONE_OVER_SIXTY_CELSIUS = .001666667;

    private final static String RESOURCE_ID = "resourceId";
    private static final double NOMINAL_POWER_OFF = 0;

    private final MockAgentTracker agentTracker = new MockAgentTracker();
    private final MockConnection connection = new MockConnection("buffer");
    private final MarketBasis marketBasis = new MarketBasis("electricity", "EUR", 100, 0, 99);
    private final MockSession session = new MockSession(marketBasis);
    private final MockContext context = new MockContext(System.currentTimeMillis());

    private void reset() {
        agentTracker.reset();
        connection.reset();
        session.reset();
    }

    private BufferRegistration<Temperature> singleActuatorRegistration() {
        Actuator actuator = new Actuator(0, "HeatPump", CommoditySet.onlyElectricity);
        return new BufferRegistration<Temperature>(RESOURCE_ID,
                                                   context.currentTime(),
                                                   Measure.zero(SI.SECOND),
                                                   "Temerature",
                                                   SI.CELSIUS,
                                                   Collections.singletonList(actuator));
    }

    private BufferSystemDescription systemDescription(BufferRegistration<Temperature> registration) {
        Timer minOnTimer = new Timer(0, "minOnTimer", Measure.valueOf(10, SI.SECOND));
        Timer minOffTimer = new Timer(1, "minOffTimer", Measure.valueOf(10, SI.SECOND));

        FillLevelFunction<RunningModeBehaviour> offFF = FillLevelFunction.<RunningModeBehaviour> create(20)
                                                                         .add(120,
                                                                              new RunningModeBehaviour(0,
                                                                                                       CommodityMeasurables.electricity(Measure.valueOf(NOMINAL_POWER_OFF,
                                                                                                                                                        SI.WATT)),
                                                                                                       Measure.valueOf(0,
                                                                                                                       NonSI.EUR_PER_HOUR)))
                                                                         .build();

        RunningMode<FillLevelFunction<RunningModeBehaviour>> off = new RunningMode<FillLevelFunction<RunningModeBehaviour>>(0,
                                                                                                                            "off",
                                                                                                                            offFF,
                                                                                                                            Collections.singleton(Transition.create(1)
                                                                                                                                                            .starts(minOnTimer)
                                                                                                                                                            .isBlockedBy(minOffTimer)
                                                                                                                                                            .build()));

        FillLevelFunction<RunningModeBehaviour> onFF = FillLevelFunction.<RunningModeBehaviour> create(20)
                                                                        .add(120,
                                                                             new RunningModeBehaviour(FILL_RATE_ONE_OVER_SIXTY_CELSIUS,
                                                                                                      CommodityMeasurables.electricity(Measure.valueOf(NOMINAL_POWER_ON,
                                                                                                                                                       SI.WATT)),
                                                                                                      Measure.valueOf(0,
                                                                                                                      NonSI.EUR_PER_HOUR)))
                                                                        .build();

        RunningMode<FillLevelFunction<RunningModeBehaviour>> on = new RunningMode<FillLevelFunction<RunningModeBehaviour>>(1,
                                                                                                                           "on",
                                                                                                                           onFF,
                                                                                                                           Collections.singleton(Transition.create(0)
                                                                                                                                                           .starts(minOffTimer)
                                                                                                                                                           .isBlockedBy(minOnTimer)
                                                                                                                                                           .build()));
        Collection<RunningMode<FillLevelFunction<RunningModeBehaviour>>> runningModes = new ArrayList<RunningMode<FillLevelFunction<RunningModeBehaviour>>>();
        runningModes.add(on);
        runningModes.add(off);
        ActuatorBehaviour ab = new ActuatorBehaviour(0, runningModes);
        FillLevelFunction<LeakageRate> bufferLeakage = FillLevelFunction.<LeakageRate> create(20)
                                                                        .add(120, new LeakageRate(0.01))
                                                                        .build();
        BufferSystemDescription bsd = new BufferSystemDescription(registration,
                                                                  context.currentTime(),
                                                                  context.currentTime(),
                                                                  Collections.singleton(ab),
                                                                  bufferLeakage);
        return bsd;
    }

    private BufferSystemDescription producingBufferSystemDescription(BufferRegistration<Temperature> registration) {
        Timer minConsumeTimer = new Timer(0, "minConsumeTimer", Measure.valueOf(10, SI.SECOND));
        Timer minOffTimer = new Timer(1, "minOffTimer", Measure.valueOf(10, SI.SECOND));
        Timer minProduceTimer = new Timer(2, "minConsumeTimer", Measure.valueOf(10, SI.SECOND));

        Set<Transition> transitionsFromOff = new HashSet<Transition>();
        transitionsFromOff.add(Transition.create(2)
                                         .starts(minProduceTimer)
                                         .isBlockedBy(minOffTimer)
                                         .isBlockedBy(minConsumeTimer)
                                         .build());
        transitionsFromOff.add(Transition.create(1)
                                         .starts(minConsumeTimer)
                                         .isBlockedBy(minOffTimer)
                                         .isBlockedBy(minProduceTimer)
                                         .build());

        RunningMode<FillLevelFunction<RunningModeBehaviour>> off = new RunningMode<FillLevelFunction<RunningModeBehaviour>>(0,
                                                                                                                            "off",
                                                                                                                            FillLevelFunction.<RunningModeBehaviour> create(-20)
                                                                                                                                             .add(120,
                                                                                                                                                  new RunningModeBehaviour(0,
                                                                                                                                                                           CommodityMeasurables.electricity(Measure.valueOf(NOMINAL_POWER_OFF,
                                                                                                                                                                                                                            SI.WATT)),
                                                                                                                                                                           Measure.valueOf(0,
                                                                                                                                                                                           NonSI.EUR_PER_HOUR)))
                                                                                                                                             .build(),
                                                                                                                            transitionsFromOff);

        RunningMode<FillLevelFunction<RunningModeBehaviour>> consume = new RunningMode<FillLevelFunction<RunningModeBehaviour>>(1,
                                                                                                                                "consume",
                                                                                                                                FillLevelFunction.<RunningModeBehaviour> create(20)
                                                                                                                                                 .add(120,
                                                                                                                                                      new RunningModeBehaviour(1,
                                                                                                                                                                               CommodityMeasurables.electricity(Measure.valueOf(NOMINAL_POWER_ON,
                                                                                                                                                                                                                                SI.WATT)),
                                                                                                                                                                               Measure.valueOf(0,
                                                                                                                                                                                               NonSI.EUR_PER_HOUR)))
                                                                                                                                                 .build(),
                                                                                                                                Collections.singleton(Transition.create(0)
                                                                                                                                                                .starts(minOffTimer)
                                                                                                                                                                .isBlockedBy(minConsumeTimer)
                                                                                                                                                                .isBlockedBy(minProduceTimer)
                                                                                                                                                                .build()));

        RunningMode<FillLevelFunction<RunningModeBehaviour>> produce = new RunningMode<FillLevelFunction<RunningModeBehaviour>>(2,
                                                                                                                                "produce",
                                                                                                                                FillLevelFunction.<RunningModeBehaviour> create(20)
                                                                                                                                                 .add(120,
                                                                                                                                                      new RunningModeBehaviour(-1,
                                                                                                                                                                               CommodityMeasurables.electricity(Measure.valueOf(-1000,
                                                                                                                                                                                                                                SI.WATT)),
                                                                                                                                                                               Measure.valueOf(0,
                                                                                                                                                                                               NonSI.EUR_PER_HOUR)))
                                                                                                                                                 .build(),
                                                                                                                                Collections.singleton(Transition.create(0)
                                                                                                                                                                .starts(minOffTimer)
                                                                                                                                                                .isBlockedBy(minConsumeTimer)
                                                                                                                                                                .build()));

        Collection<RunningMode<FillLevelFunction<RunningModeBehaviour>>> runningModes = new ArrayList<RunningMode<FillLevelFunction<RunningModeBehaviour>>>();
        runningModes.add(consume);
        runningModes.add(off);
        runningModes.add(produce);
        return new BufferSystemDescription(registration,
                                           context.currentTime(),
                                           context.currentTime(),
                                           Collections.singleton(new ActuatorBehaviour(0, runningModes)),
                                           FillLevelFunction.<LeakageRate> create(-20)
                                                            .add(120, new LeakageRate(ONE_HUNDREDTH))
                                                            .build());
    }

    private BufferAgent<Temperature> createAgent() {
        BufferAgent<Temperature> agent = new BufferAgent<Temperature>(connection, agentTracker, "agent-1", "matcher");
        agent.setContext(context);
        agent.connectToMatcher(session);
        return agent;
    }

    /**
     * Test: Agent has not received message but does receive price update
     *
     * Expected behavior: Agent does nothing and doesn't break
     */
    public void testNoRegistration() {
        reset();
        BufferAgent<Temperature> agent = createAgent();
        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), 0));
        assertNull(connection.getLastReceivedMessage());
        assertFalse(agentTracker.hasUnregistered());
    }

    /**
     * Test: Agent has not received a BufferSystemDescirption but does receive price update
     *
     * Expected behavior: Agent does nothing and doesn't break
     */
    public void testNoSystemDescription() {
        reset();
        BufferAgent<Temperature> agent = createAgent();
        agent.handleControlSpaceRegistration(singleActuatorRegistration());
        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), 0));
        assertNull(connection.getLastReceivedMessage());
        assertFalse(agentTracker.hasUnregistered());
    }

    /**
     * Test: Agent has two runningmodes, but the other is currently blocked by a timer
     *
     * Expected behavior: Agent creates a bid without flexibility
     */
    public void testTimerBlocking() {
        reset();
        BufferAgent<Temperature> agent = createAgent();

        BufferRegistration<Temperature> registration = singleActuatorRegistration();
        agent.handleMessage(registration);

        BufferSystemDescription bsd = systemDescription(registration);
        agent.handleMessage(bsd);

        TimerUpdate minOnTimer = new TimerUpdate(0, new Date(context.currentTimeMillis() + 5000)); // blocking
        ActuatorUpdate au = new ActuatorUpdate(0, 1, Collections.singleton(minOnTimer)); // current running mode is on
        BufferStateUpdate<Temperature> bsu = new BufferStateUpdate<Temperature>(registration,
                                                                                context.currentTime(),
                                                                                context.currentTime(),
                                                                                Measure.valueOf(60, SI.CELSIUS),
                                                                                Collections.singleton(au));

        agent.handleMessage(bsu);

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), 0));

        // Bid should not have flexibility
        Bid bid = session.getLastBid().getBid();
        BidAnalyzer.assertFlatBidWithValue(bid, Measure.valueOf(NOMINAL_POWER_ON, SI.WATT));
    }

    /**
     * Test: Agent has two running modes, but they are not connected through a transition
     *
     * Expected behavior: Agent creates a bid without flexibility
     */
    public void testNoTransition() {
        reset();
        BufferAgent<Temperature> agent = createAgent();

        BufferRegistration<Temperature> registration = singleActuatorRegistration();
        agent.handleMessage(registration);

        // Create system description.

        FillLevelFunction<RunningModeBehaviour> offFF = FillLevelFunction.<RunningModeBehaviour> create(20)
                                                                         .add(120,
                                                                              new RunningModeBehaviour(1,
                                                                                                       CommodityMeasurables.electricity(Measure.valueOf(0,
                                                                                                                                                        SI.WATT)),
                                                                                                       Measure.valueOf(0,
                                                                                                                       NonSI.EUR_PER_HOUR)))
                                                                         .build();

        RunningMode<FillLevelFunction<RunningModeBehaviour>> off = new RunningMode<FillLevelFunction<RunningModeBehaviour>>(0,
                                                                                                                            "off",
                                                                                                                            offFF,
                                                                                                                            Collections.<Transition> emptySet());

        FillLevelFunction<RunningModeBehaviour> onFF = FillLevelFunction.<RunningModeBehaviour> create(20)
                                                                        .add(120,
                                                                             new RunningModeBehaviour(1,
                                                                                                      CommodityMeasurables.electricity(Measure.valueOf(NOMINAL_POWER_ON,
                                                                                                                                                       SI.WATT)),
                                                                                                      Measure.valueOf(0,
                                                                                                                      NonSI.EUR_PER_HOUR)))
                                                                        .build();
        // There is no way to leave this mode.
        RunningMode<FillLevelFunction<RunningModeBehaviour>> on = new RunningMode<FillLevelFunction<RunningModeBehaviour>>(1,
                                                                                                                           "on",
                                                                                                                           onFF,
                                                                                                                           Collections.<Transition> emptySet());
        Collection<RunningMode<FillLevelFunction<RunningModeBehaviour>>> runningModes = new ArrayList<RunningMode<FillLevelFunction<RunningModeBehaviour>>>();
        runningModes.add(on);
        runningModes.add(off);
        ActuatorBehaviour ab = new ActuatorBehaviour(0, runningModes);
        FillLevelFunction<LeakageRate> bufferLeakage = FillLevelFunction.<LeakageRate> create(20)
                                                                        .add(120, new LeakageRate(ONE_HUNDREDTH))
                                                                        .build();
        BufferSystemDescription bsd = new BufferSystemDescription(registration,
                                                                  context.currentTime(),
                                                                  context.currentTime(),
                                                                  Collections.singleton(ab),
                                                                  bufferLeakage);

        agent.handleMessage(bsd);

        // Create BufferStateUpdate

        ActuatorUpdate au = new ActuatorUpdate(0, 1, null); // current running mode
                                                            // is on
        BufferStateUpdate<Temperature> bsu = new BufferStateUpdate<Temperature>(registration,
                                                                                context.currentTime(),
                                                                                context.currentTime(),
                                                                                Measure.valueOf(60, SI.CELSIUS),
                                                                                Collections.singleton(au));

        agent.handleMessage(bsu);

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), 0));

        // Bid should not have flexibility
        Bid bid = session.getLastBid().getBid();
        BidAnalyzer.assertFlatBidWithValue(bid, Measure.valueOf(NOMINAL_POWER_ON, SI.WATT));
    }

    /**
     * Test: Agent has a blocking timer but it is not set.
     *
     * Expected behavior: Agent produces a flexible bid.
     */
    public void testBlockingTimerNotSet() {
        reset();
        FpaiAgent agent = createAgent();

        BufferRegistration<Temperature> registration = singleActuatorRegistration();
        agent.handleMessage(registration);

        agent.handleMessage(systemDescription(registration));

        // Actuator is on, but there are no timer updates.

        agent.handleMessage(new BufferStateUpdate<Temperature>(registration,
                                                               context.currentTime(),
                                                               context.currentTime(),
                                                               Measure.valueOf(35, SI.CELSIUS),
                                                               Collections.<ActuatorUpdate> singleton(new ActuatorUpdate(0,
                                                                                                                         1,
                                                                                                                         Collections.<TimerUpdate> emptySet()))));

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), 0));

        // Bid should have flexibility
        Bid bid2 = session.getLastBid().getBid();
        System.out.println("bid with no sys update" + bid2);
        BidAnalyzer.assertStepBid(bid2);
    }

    /**
     * Test: The timer has just finished.
     *
     * The agents should produce a step bid.
     */
    public void testBlockingTimerFinished() {
        reset();
        BufferAgent<Temperature> agent = createAgent();

        BufferRegistration<Temperature> registration = singleActuatorRegistration();
        agent.handleMessage(registration);

        BufferSystemDescription bsd = systemDescription(registration);
        agent.handleMessage(bsd);

        // The timer just finished.
        TimerUpdate minOnTimer = new TimerUpdate(0, new Date(context.currentTimeMillis() - 1));
        ActuatorUpdate au = new ActuatorUpdate(0, 1, Collections.singleton(minOnTimer));
        BufferStateUpdate<Temperature> bsu = new BufferStateUpdate<Temperature>(registration,
                                                                                context.currentTime(),
                                                                                context.currentTime(),
                                                                                Measure.valueOf(60, SI.CELSIUS),
                                                                                Collections.singleton(au));

        agent.handleMessage(bsu);

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), 0));

        // Bid should not have flexibility
        Bid bid = session.getLastBid().getBid();
        BidAnalyzer.assertStepBid(bid);
    }

    /**
     * Test: Must-run forces agent to allocate the same running mode regardless of price.
     */
    public void testBidResponseMustRun() {
        reset();
        BufferAgent<Temperature> agent = createAgent();
        BufferRegistration<Temperature> registration = singleActuatorRegistration();
        agent.handleMessage(registration);

        agent.handleMessage(systemDescription(registration));

        TimerUpdate minOnTimer = new TimerUpdate(0, new Date(context.currentTimeMillis() + 5000)); // blocking
        ActuatorUpdate au = new ActuatorUpdate(0, 1, Collections.singleton(minOnTimer)); // current running mode is on
        // This is a must-on situation.
        BufferStateUpdate<Temperature> bsu = new BufferStateUpdate<Temperature>(registration,
                                                                                context.currentTime(),
                                                                                context.currentTime(),
                                                                                Measure.valueOf(60, SI.CELSIUS),
                                                                                Collections.singleton(au));

        agent.handleMessage(bsu);

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), 1));

        Assert.assertEquals(1, (((BufferAllocation) (connection.getLastReceivedMessage())).getActuatorAllocations()
                                                                                          .iterator()
                                                                                          .next().getRunningModeId()));

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMinimumPrice()), 1));

        Assert.assertEquals(1, (((BufferAllocation) (connection.getLastReceivedMessage())).getActuatorAllocations()
                                                                                          .iterator()
                                                                                          .next().getRunningModeId()));

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMaximumPrice()), 1));

        Assert.assertEquals(1, (((BufferAllocation) (connection.getLastReceivedMessage())).getActuatorAllocations()
                                                                                          .iterator()
                                                                                          .next().getRunningModeId()));

    }

    /**
     * Test: Must-run forces agent to allocate the same running mode regardless of price.
     */
    public void testBidResponseFlexible() {
        reset();
        BufferAgent<Temperature> agent = createAgent();
        BufferRegistration<Temperature> registration = singleActuatorRegistration();
        agent.handleMessage(registration);

        agent.handleMessage(systemDescription(registration));

        // Attention: The minimum on timer is running, but the current running mode is off, so it should not be bothered
        // by it.
        TimerUpdate minOnTimer = new TimerUpdate(0, new Date(context.currentTimeMillis() + 1000));
        ActuatorUpdate au = new ActuatorUpdate(0, 0, Collections.singleton(minOnTimer));
        // current running mode is off
        BufferStateUpdate<Temperature> bsu = new BufferStateUpdate<Temperature>(registration,
                                                                                context.currentTime(),
                                                                                context.currentTime(),
                                                                                Measure.valueOf(60, SI.CELSIUS),
                                                                                Collections.singleton(au));

        agent.handleMessage(bsu);

        // Price around halfway means we should do what exactly?
        // agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), 0));
        // Assert.assertEquals(1, (((BufferAllocation) (connection.getLastReceivedMessage())).getActuatorAllocations()
        // .iterator()
        // .next().getRunningModeId()));

        // Minimum price means go on!
        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMinimumPrice()), 1));

        Assert.assertEquals(1, (((BufferAllocation) (connection.getLastReceivedMessage())).getActuatorAllocations()
                                                                                          .iterator()
                                                                                          .next().getRunningModeId()));

        // Maximum price means go off!
        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMaximumPrice()), 1));

        Assert.assertEquals(0, (((BufferAllocation) (connection.getLastReceivedMessage())).getActuatorAllocations()
                                                                                          .iterator()
                                                                                          .next().getRunningModeId()));
    }

    /**
     * Test: Buffer is exactly full.
     *
     * Should produce a bid that will go off for all prices (effectively, though it may technically be a step function,
     * where the 'on part' has zero length).
     */
    public void testBufferFull() {
        reset();
        BufferAgent<Temperature> agent = createAgent();

        BufferRegistration<Temperature> registration = singleActuatorRegistration();
        agent.handleMessage(registration);

        agent.handleMessage(systemDescription(registration));

        TimerUpdate minOnTimer = new TimerUpdate(0, new Date(context.currentTimeMillis() - 1));
        ActuatorUpdate au = new ActuatorUpdate(0, 0, Collections.singleton(minOnTimer));
        // current running mode is off
        BufferStateUpdate<Temperature> bsu = new BufferStateUpdate<Temperature>(registration,
                                                                                context.currentTime(),
                                                                                context.currentTime(),
                                                                                Measure.valueOf(120, SI.CELSIUS),
                                                                                Collections.singleton(au));

        agent.handleMessage(bsu);

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMinimumPrice()), 1));

        System.out.println(connection.getLastReceivedMessage());
        Assert.assertEquals(0, (((BufferAllocation) (connection.getLastReceivedMessage())).getActuatorAllocations()
                                                                                          .iterator()
                                                                                          .next().getRunningModeId()));

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMaximumPrice()), 1));

        Assert.assertEquals(0, (((BufferAllocation) (connection.getLastReceivedMessage())).getActuatorAllocations()
                                                                                          .iterator()
                                                                                          .next().getRunningModeId()));

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), 1));

        Assert.assertEquals(0, (((BufferAllocation) (connection.getLastReceivedMessage())).getActuatorAllocations()
                                                                                          .iterator()
                                                                                          .next().getRunningModeId()));

    }

    /**
     * Test: This test sees what happens when the fill level is over full and how the agent responds.
     *
     * Agents should go off for all prices.
     */
    public void testBufferOverFull() {
        reset();
        BufferAgent<Temperature> agent = createAgent();
        BufferRegistration<Temperature> registration = singleActuatorRegistration();
        agent.handleMessage(registration);

        agent.handleMessage(systemDescription(registration));

        // No blocking timers.
        TimerUpdate minOnTimer = new TimerUpdate(0, new Date(context.currentTimeMillis() - 1));
        ActuatorUpdate au = new ActuatorUpdate(0, 0, Collections.singleton(minOnTimer));
        // Current running mode is off.
        BufferStateUpdate<Temperature> bsu = new BufferStateUpdate<Temperature>(registration,
                                                                                context.currentTime(),
                                                                                context.currentTime(),
                                                                                Measure.valueOf(140, SI.CELSIUS),
                                                                                Collections.singleton(au));
        agent.handleMessage(bsu);

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMinimumPrice()), 0));
        agent.handleMessage(bsu);
        BidUpdate newBid = session.getLastBid();

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMaximumPrice()),
                                                newBid.getBidNumber()));
        BidUpdate newerBid = session.getLastBid();

        BidAnalyzer.assertFlatBidWithValue(newBid.getBid(), Measure.valueOf(NOMINAL_POWER_OFF, SI.WATT));
        BidAnalyzer.assertFlatBidWithValue(newerBid.getBid(), Measure.valueOf(NOMINAL_POWER_OFF, SI.WATT));
    }

    /**
     * Test: Buffer is empty.
     *
     * It should go on at any price!
     */
    public void testBufferEmpty() {
        reset();
        BufferAgent<Temperature> agent = createAgent();
        BufferRegistration<Temperature> registration = singleActuatorRegistration();
        agent.handleMessage(registration);

        agent.handleMessage(systemDescription(registration));

        // No blocking timers.
        TimerUpdate minOnTimer = new TimerUpdate(1, new Date(context.currentTimeMillis() - 1));
        ActuatorUpdate au = new ActuatorUpdate(0, 0, Collections.singleton(minOnTimer));
        // Current running mode is off.
        BufferStateUpdate<Temperature> bsu = new BufferStateUpdate<Temperature>(registration,
                                                                                context.currentTime(),
                                                                                context.currentTime(),
                                                                                Measure.valueOf(20, SI.CELSIUS),
                                                                                Collections.singleton(au));
        agent.handleMessage(bsu);

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMinimumPrice()), 1));

        System.out.println(connection.getLastReceivedMessage());
        Assert.assertEquals(1, (((BufferAllocation) (connection.getLastReceivedMessage())).getActuatorAllocations()
                                                                                          .iterator()
                                                                                          .next().getRunningModeId()));

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMaximumPrice()), 1));
        System.out.println("faler:" + connection.getLastReceivedMessage());

        // Bid should not have flexibility
        BidUpdate bid = session.getLastBid();

        System.out.println("faler:" + bid);
        Assert.assertEquals(1, (((BufferAllocation) (connection.getLastReceivedMessage())).getActuatorAllocations()
                                                                                          .iterator()
                                                                                          .next().getRunningModeId()));

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), bid.getBidNumber()));

        Assert.assertEquals(1, (((BufferAllocation) (connection.getLastReceivedMessage())).getActuatorAllocations()
                                                                                          .iterator()
                                                                                          .next().getRunningModeId()));
    }

    /**
     * A producing buffer should produce bids that have production and consumption in them.
     */
    public void testProducingBuffer() {
        reset();
        BufferAgent<Temperature> agent = createAgent();
        BufferRegistration<Temperature> registration = singleActuatorRegistration();
        agent.handleMessage(registration);

        agent.handleMessage(producingBufferSystemDescription(registration));

        ActuatorUpdate au = new ActuatorUpdate(0, 0, null);
        // current running mode is Off. Buffer is half full.
        BufferStateUpdate<Temperature> bsu = new BufferStateUpdate<Temperature>(registration,
                                                                                context.currentTime(),
                                                                                context.currentTime(),
                                                                                Measure.valueOf(70, SI.CELSIUS),
                                                                                Collections.singleton(au));

        agent.handleMessage(bsu);

        // Price is low.
        BidUpdate bid = session.getLastBid();
        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMinimumPrice()),
                                                bid.getBidNumber()));

        Assert.assertEquals(NOMINAL_POWER_ON, bid.getBid().getMaximumDemand());
        Assert.assertEquals(-1000d, bid.getBid().getMinimumDemand());

        Assert.assertEquals(1, (((BufferAllocation) (connection.getLastReceivedMessage())).getActuatorAllocations()
                                                                                          .iterator()
                                                                                          .next().getRunningModeId()));

        // Current running mode is Off. Buffer is empty.
        BufferStateUpdate<Temperature> bsu2 = new BufferStateUpdate<Temperature>(registration,
                                                                                 context.currentTime(),
                                                                                 context.currentTime(),
                                                                                 Measure.valueOf(-20, SI.CELSIUS),
                                                                                 Collections.singleton(au));
        agent.handleMessage(bsu2);

        // Price is at maximum.
        BidUpdate bid2 = session.getLastBid();
        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMaximumPrice()),
                                                bid2.getBidNumber()));
        System.out.println("overempty: " + bid2);

        Assert.assertEquals(NOMINAL_POWER_ON, bid2.getBid().getMaximumDemand());
        Assert.assertEquals(NOMINAL_POWER_ON, bid2.getBid().getMinimumDemand());

        // Should consume when at maximum price.
        Assert.assertEquals(1, (((BufferAllocation) (connection.getLastReceivedMessage())).getActuatorAllocations()
                                                                                          .iterator()
                                                                                          .next().getRunningModeId()));

        // Current running mode is Off. Buffer is full.
        BufferStateUpdate<Temperature> bsu3 = new BufferStateUpdate<Temperature>(registration,
                                                                                 context.currentTime(),
                                                                                 context.currentTime(),
                                                                                 Measure.valueOf(120, SI.CELSIUS),
                                                                                 Collections.singleton(au));
        agent.handleMessage(bsu3);

        // Price is middle.
        BidUpdate bid3 = session.getLastBid();
        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, 50), bid3.getBidNumber()));
        System.out.println("overfull: " + bid3);

        Assert.assertEquals(-1000d, bid3.getBid().getMaximumDemand());
        Assert.assertEquals(-1000d, bid3.getBid().getMinimumDemand());

        // Should discharge (empty buffer) when buffer is full.
        Assert.assertEquals(2, (((BufferAllocation) (connection.getLastReceivedMessage())).getActuatorAllocations()
                                                                                          .iterator()
                                                                                          .next().getRunningModeId()));
    }

    /**
     * Test: A new system description is received with widely differing FillLevelFunctions. A price update now triggers
     * a bid update.
     *
     * The previous state update is not used for making the bid. Rather, a good default is chosen until the first
     * StateUpdate message arrives.
     */
    public void testNewSystemDescription() {
        reset();
        BufferAgent<Temperature> agent = createAgent();
        BufferRegistration<Temperature> registration = singleActuatorRegistration();
        agent.handleMessage(registration);

        agent.handleMessage(producingBufferSystemDescription(registration));

        ActuatorUpdate au = new ActuatorUpdate(0, 0, null);
        // current running mode is Off. Buffer is half full.

        agent.handleMessage(new BufferStateUpdate<Temperature>(registration,
                                                               context.currentTime(),
                                                               context.currentTime(),
                                                               Measure.valueOf(70, SI.CELSIUS),
                                                               Collections.singleton(au)));
        Bid oldBid = session.getLastBid().getBid();

        FillLevelFunction<RunningModeBehaviour> offFF = FillLevelFunction.<RunningModeBehaviour> create(200)
                                                                         .add(520,
                                                                              new RunningModeBehaviour(0d,
                                                                                                       CommodityMeasurables.electricity(Measure.valueOf(1d,
                                                                                                                                                        SI.WATT)),
                                                                                                       Measure.valueOf(0.02,
                                                                                                                       NonSI.EUR_PER_HOUR)))
                                                                         .build();

        RunningMode<FillLevelFunction<RunningModeBehaviour>> off = new RunningMode<FillLevelFunction<RunningModeBehaviour>>(1,
                                                                                                                            "off",
                                                                                                                            offFF,
                                                                                                                            Collections.<Transition> emptySet());

        agent.handleMessage(new BufferSystemDescription(registration,
                                                        context.currentTime(),
                                                        context.currentTime(),
                                                        Collections.<ActuatorBehaviour> singleton(new ActuatorBehaviour(0,
                                                                                                                        Collections.<RunningMode<FillLevelFunction<RunningModeBehaviour>>> singleton(off))),
                                                        FillLevelFunction.<LeakageRate> create(200)
                                                                         .add(520, new LeakageRate(ONE_HUNDREDTH))
                                                                         .build()));
        // The new system description leads to an invalid fill level, so the agent should not send out a new bid.
        Assert.assertEquals(oldBid, session.getLastBid().getBid());
    }

    /**
     * Test: What happens when the buffer is under or overfull.
     */
    public void testOverfullUnderfull() {
        reset();
        BufferAgent<Temperature> agent = createAgent();
        BufferRegistration<Temperature> registration = singleActuatorRegistration();
        agent.handleMessage(registration);

        agent.handleMessage(systemDescription(registration));

        // No blocking timers.
        TimerUpdate minOnTimer = new TimerUpdate(1, new Date(context.currentTimeMillis() - 1));
        ActuatorUpdate au = new ActuatorUpdate(0, 0, Collections.singleton(minOnTimer));

        // Current running mode is off. Buffer is underfilled.
        agent.handleMessage(new BufferStateUpdate<Temperature>(registration,
                                                               context.currentTime(),
                                                               context.currentTime(),
                                                               Measure.valueOf(19, SI.CELSIUS),
                                                               Collections.singleton(au)));

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMinimumPrice()), 1));

        // The agent should send a must run bid and an allocation for ON when the fill level is too low.
        BidAnalyzer.assertFlatBidWithValue(session.getLastBid().getBid(), Measure.valueOf(NOMINAL_POWER_ON, SI.WATT));
        BufferAllocation allocation = (BufferAllocation) connection.getLastReceivedMessage();
        Assert.assertEquals(0, allocation.getActuatorAllocations().iterator().next().getActuatorId());
        Assert.assertEquals(1, allocation.getActuatorAllocations().iterator().next().getRunningModeId());

        agent.handleMessage(new BufferStateUpdate<Temperature>(registration,
                                                               context.currentTime(),
                                                               context.currentTime(),
                                                               Measure.valueOf(1900, SI.CELSIUS),
                                                               Collections.singleton(au)));

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMinimumPrice()), 1));

        // The agent should not send out an allocation when the fill level is too high.
        BidAnalyzer.assertFlatBidWithValue(session.getLastBid().getBid(), Measure.valueOf(NOMINAL_POWER_OFF, SI.WATT));
        BufferAllocation allocationNewer = (BufferAllocation) connection.getLastReceivedMessage();
        Assert.assertEquals(0, allocationNewer.getActuatorAllocations().iterator().next().getActuatorId());
        Assert.assertEquals(0, allocationNewer.getActuatorAllocations().iterator().next().getRunningModeId());
    }

    /**
     * Test: The target profile is set to one hour ahead and requires the fill level to be minimum 80% then.
     *
     * The bid should continue to be more willing to pay as time goes by, until the latest start time when a must run
     * bid should be given.
     */
    public void testTargetProfile() {
        reset();
        BufferAgent<Temperature> agent = createAgent();
        BufferRegistration<Temperature> registration = singleActuatorRegistration();
        agent.handleMessage(registration);

        agent.handleMessage(systemDescription(registration));

        // No blocking timers.
        TimerUpdate minOnTimer = new TimerUpdate(1, new Date(context.currentTimeMillis() - 1));
        ActuatorUpdate au = new ActuatorUpdate(0, 0, Collections.singleton(minOnTimer));
        // Current running mode is off. Buffer is half full.
        agent.handleMessage(new BufferStateUpdate<Temperature>(registration,
                                                               context.currentTime(),
                                                               context.currentTime(),
                                                               Measure.valueOf(70, SI.CELSIUS),
                                                               Collections.singleton(au)));

        BidUpdate bidT0 = session.getLastBid();

        agent.handlePriceUpdate(new PriceUpdate(new Price(marketBasis, marketBasis.getMaximumPrice()),
                                                bidT0.getBidNumber()));

        // This is valid from one hour from now.
        Date validFrom = new Date(context.currentTimeMillis() + 1000 * 60 * 60);
        // Creates a single constraint profile one hour from now with minimum 100 and maximum 120 degrees.
        ConstraintProfile<Temperature> constraintProfile = ConstraintProfile.<Temperature> create()
                                                                            .duration(Measure.valueOf(60 * 15,
                                                                                                      SI.SECOND))
                                                                            .add(new Constraint<Temperature>(Measure.valueOf(100,
                                                                                                                             SI.CELSIUS),
                                                                                                             Measure.valueOf(120,
                                                                                                                             SI.CELSIUS)))
                                                                            .build();

        agent.handleMessage(new BufferTargetProfileUpdate<Temperature>(registration,
                                                                       context.currentTime(),
                                                                       validFrom,
                                                                       constraintProfile));
        // This target profile update should trigger a bid update.
        BidUpdate bidT1 = session.getLastBid();

        // Jump ahead to 30 minutes before the deadline.
        context.jump(30 * 60 * 1000);

        agent.handleMessage(new BufferStateUpdate<Temperature>(registration,
                                                               context.currentTime(),
                                                               context.currentTime(),
                                                               Measure.valueOf(70, SI.CELSIUS),
                                                               Collections.singleton(au)));

        BidUpdate bidT1b = session.getLastBid();

        // Jump ahead to the profile deadline.
        context.jump(30 * 60 * 1000);

        agent.handleMessage(new BufferStateUpdate<Temperature>(registration,
                                                               context.currentTime(),
                                                               context.currentTime(),
                                                               Measure.valueOf(70, SI.CELSIUS),
                                                               Collections.singleton(au)));
        BidUpdate bidT2 = session.getLastBid();

        // Jump ahead 10 minutes (within the profile).
        context.jump(10 * 60 * 1000);

        agent.handleMessage(new BufferStateUpdate<Temperature>(registration,
                                                               context.currentTime(),
                                                               context.currentTime(),
                                                               Measure.valueOf(70, SI.CELSIUS),
                                                               Collections.singleton(au)));
        BidUpdate bidT3 = session.getLastBid();

        // Jump ahead 10 minutes (the profile has expired now, so it should be flexible again).
        context.jump(10 * 60 * 1000);

        agent.handleMessage(new BufferStateUpdate<Temperature>(registration,
                                                               context.currentTime(),
                                                               context.currentTime(),
                                                               Measure.valueOf(70, SI.CELSIUS),
                                                               Collections.singleton(au)));
        BidUpdate bidT4 = session.getLastBid();

        // In this scenario the buffer is always half full, except at T1b (where the buffer is halfway between the
        // target
        // profile constraints).
        // Bid T0 is before the target profile is set.
        // Bid T1 is when the target profile is just set and an hour away.
        // Bid T1b is when the target profile is just set and an hour away.
        // Bid T2 is exactly at the start of the profile.
        // Bid T3 is is during the target profile period.
        // Bid T4 is when the profile has ended.

        BidAnalyzer.assertStepBid(bidT0.getBid());
        // Bid T0 should be equal to T4.
        BidAnalyzer.assertBidsEqual(bidT0.getBid(), bidT4.getBid());
        // Bid T1 should be a step bid more eager than T0.
        BidAnalyzer.assertDemandSumGreaterThan(bidT1.getBid(), bidT0.getBid());
        // Bid T1b should be a step bid more eager than T1.
        BidAnalyzer.assertDemandSumGreaterThan(bidT1b.getBid(), bidT1.getBid());
        // Bid T2 at the deadline should effectively be a must run bid.
        BidAnalyzer.assertDemandAtLeast(bidT2.getBid(), Measure.valueOf(NOMINAL_POWER_ON, SI.WATT));
        // Bid T3 should be equal to T0, because at T3 the fill level is halfway between the target profile bounds.
        BidAnalyzer.assertBidsEqual(bidT0.getBid(), bidT3.getBid());
        // Bid T4 is when the target profile has expired, so like T0. Already asserted above.
    }
}
