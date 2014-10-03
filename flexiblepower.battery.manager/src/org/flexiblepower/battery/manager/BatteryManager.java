package org.flexiblepower.battery.manager;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Money;
import javax.measure.quantity.MoneyFlow;
import javax.measure.quantity.Power;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.flexiblepower.battery.manager.BatteryManager.Config;
import org.flexiblepower.efi.BufferResourceManager;
import org.flexiblepower.efi.buffer.Actuator;
import org.flexiblepower.efi.buffer.ActuatorAllocation;
import org.flexiblepower.efi.buffer.ActuatorBehaviour;
import org.flexiblepower.efi.buffer.ActuatorUpdate;
import org.flexiblepower.efi.buffer.BufferAllocation;
import org.flexiblepower.efi.buffer.BufferRegistration;
import org.flexiblepower.efi.buffer.BufferStateUpdate;
import org.flexiblepower.efi.buffer.BufferSystemDescription;
import org.flexiblepower.efi.buffer.LeakageRate;
import org.flexiblepower.efi.buffer.RunningModeBehaviour;
import org.flexiblepower.efi.util.FillLevelFunction;
import org.flexiblepower.efi.util.RunningMode;
import org.flexiblepower.efi.util.Timer;
import org.flexiblepower.efi.util.Transition;
import org.flexiblepower.messaging.Cardinality;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.messaging.Ports;
import org.flexiblepower.rai.AllocationRevoke;
import org.flexiblepower.rai.AllocationStatus;
import org.flexiblepower.rai.AllocationStatusUpdate;
import org.flexiblepower.rai.ControlSpaceRevoke;
import org.flexiblepower.rai.ResourceMessage;
import org.flexiblepower.rai.values.CommodityMeasurables;
import org.flexiblepower.rai.values.CommoditySet;
import org.flexiblepower.ral.drivers.battery.BatteryControlParameters;
import org.flexiblepower.ral.drivers.battery.BatteryMode;
import org.flexiblepower.ral.drivers.battery.BatteryState;
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.time.TimeService;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = Endpoint.class, immediate = true)
@Ports({ @Port(name = "driver", sends = BatteryControlParameters.class, accepts = BatteryState.class),
        @Port(name = "controller",
              accepts = { BufferAllocation.class, AllocationRevoke.class },
              sends = { BufferRegistration.class,
                       BufferStateUpdate.class,
                       AllocationStatusUpdate.class,
                       ControlSpaceRevoke.class },
              cardinality = Cardinality.SINGLE) })
public class BatteryManager extends AbstractResourceManager<BatteryState, BatteryControlParameters>
                                                                                                   implements
                                                                                                   BufferResourceManager {
    private static final Logger log = LoggerFactory.getLogger(BatteryManager.class);
    @SuppressWarnings("unchecked")
    private static final Unit<Energy> WH = (Unit<Energy>) SI.WATT.times(NonSI.HOUR); // Define WattHour (Wh)
    private static final int BATTERY_ACTUATOR_ID = 1;

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "BatteryManager", description = "Unique resourceID")
        String resourceId();
    }

    private BatteryState currentBatteryState;
    private Date changedStateTimestamp;
    Actuator batteryActuator;
    private BufferRegistration<Energy> batteryBufferRegistration;
    private Config configuration;
    private TimeService timeService;
    private ScheduledExecutorService scheduler;

    @Override
    protected List<? extends ResourceMessage> startRegistration(BatteryState batteryState) {
        currentBatteryState = batteryState;
        changedStateTimestamp = timeService.getTime();

        // Buffer registration
        batteryActuator = new Actuator(BATTERY_ACTUATOR_ID,
                                       "Battery",
                                       CommoditySet.onlyElectricity);
        Set<Actuator> actuators = new HashSet<Actuator>();
        actuators.add(batteryActuator);
        batteryBufferRegistration = new BufferRegistration<Energy>(configuration.resourceId(),
                                                                   changedStateTimestamp,
                                                                   toSeconds(0),
                                                                   "Battery level",
                                                                   WH,
                                                                   actuators);

        // Buffer system description
        ActuatorBehaviour batteryActuatorBehaviour = makeBatteryActuatorBehaviour(batteryActuator.getActuatorId());
        FillLevelFunction<LeakageRate> bufferLeakageFunction = FillLevelFunction.<LeakageRate> create(0)
                                                                                .add(6000, new LeakageRate(0.0001))
                                                                                .build();
        Set<ActuatorBehaviour> actuatorsBehaviours = new HashSet<ActuatorBehaviour>();
        actuatorsBehaviours.add(batteryActuatorBehaviour);
        BufferSystemDescription sysDescr = new BufferSystemDescription(batteryBufferRegistration,
                                                                       changedStateTimestamp,
                                                                       changedStateTimestamp,
                                                                       actuatorsBehaviours,
                                                                       bufferLeakageFunction);

        // Buffer state update, based on batteryState
        Measure<Double, Energy> currentFillLevel = getCurrentFillLevel(batteryState);
        Set<ActuatorUpdate> currentRunningMode = makeBatteryActuatorUpdateSet(batteryActuator.getActuatorId(),
                                                                              batteryState.getCurrentMode());
        BufferStateUpdate<Energy> update = new BufferStateUpdate<Energy>(batteryBufferRegistration,
                                                                         changedStateTimestamp,
                                                                         changedStateTimestamp,
                                                                         currentFillLevel,
                                                                         currentRunningMode);

        return Arrays.asList(batteryBufferRegistration, sysDescr, update);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected List<? extends ResourceMessage> updatedState(BatteryState batteryState) {
        log.debug("Receive battery state update, mode=" + batteryState.getCurrentMode());

        if (batteryState.equals(currentBatteryState)) {
            return Collections.emptyList();
        } else {
            currentBatteryState = batteryState;
            changedStateTimestamp = timeService.getTime();
            Date validFrom = changedStateTimestamp;
            Measure<Double, Energy> currentFillLevel = getCurrentFillLevel(batteryState);

            Set<ActuatorUpdate> actuatorUpdateSet = makeBatteryActuatorUpdateSet(batteryActuator.getActuatorId(),
                                                                                 batteryState.getCurrentMode());
            BufferStateUpdate<Energy> update = new BufferStateUpdate<Energy>(batteryBufferRegistration,
                                                                             changedStateTimestamp,
                                                                             validFrom,
                                                                             currentFillLevel,
                                                                             actuatorUpdateSet);
            return Arrays.asList(update);
        }
    }

    @Override
    protected BatteryControlParameters receivedAllocation(ResourceMessage message) {
        if (message instanceof BufferAllocation) {
            BufferAllocation bufferAllocation = (BufferAllocation) message;
            log.debug("Received allocation " + bufferAllocation);

            if (!bufferAllocation.isEmergencyAllocation()) {
                Set<ActuatorAllocation> allocations = bufferAllocation.getActuatorAllocations();
                for (ActuatorAllocation allocation : allocations) {
                    BatteryMode batteryMode = BatteryMode.values()[allocation.getRunningModeId()];
                    long delay = allocation.getStartTime().getTime() - timeService.getCurrentTimeMillis();
                    delay = (delay <= 0 ? 1 : delay);
                    setupAllocation(delay, batteryMode);

                    allocationStatusUpdate(new AllocationStatusUpdate(timeService.getTime(),
                                                                      bufferAllocation,
                                                                      AllocationStatus.ACCEPTED,
                                                                      ""));
                }
            }
            return null;
        } else if (message instanceof AllocationRevoke) {
            log.debug("Revocation message received");
            return null;
        } else {
            log.warn("Unexpected resource (" + message.toString() + ") message type (" + message.getClass().getName()
                     + ")received");
            return null;
        }
    }

    private void setupAllocation(final long delayInMilliSeconds, final BatteryMode mode) {
        final Runnable allocationHelper = new Runnable() {
            @Override
            public void run() {
                sendControlParameters(new BatteryControlParameters() {
                    @Override
                    public BatteryMode getMode() {
                        return mode;
                    }
                });
                log.debug("Set up allocation at " + delayInMilliSeconds + "ms for batteryMode=" + mode);
            }
        };
        scheduler.schedule(allocationHelper, delayInMilliSeconds, TimeUnit.MILLISECONDS);
    }

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        configuration = Configurable.createConfigurable(Config.class, properties);
        log.debug("Activated");
    }

    @Deactivate
    public void deactivate() {
    }

    // ---------------------------- helper classes -----------------------------

    private ActuatorBehaviour makeBatteryActuatorBehaviour(int actuatorId) {
        // transitions
        Transition toChargingTransition = makeTransition(0);
        Transition toIdleTransition = makeTransition(1);
        Transition toDisChargingTransition = makeTransition(2);

        Set<Transition> idleTransition = new HashSet<Transition>();
        idleTransition.add(toChargingTransition);
        idleTransition.add(toDisChargingTransition);
        Set<Transition> chargeTransition = new HashSet<Transition>();
        chargeTransition.add(toIdleTransition);
        chargeTransition.add(toDisChargingTransition);
        Set<Transition> dischargeTransition = new HashSet<Transition>();
        dischargeTransition.add(toIdleTransition);
        dischargeTransition.add(toChargingTransition);

        // running battery modes
        FillLevelFunction<RunningModeBehaviour> idleFillLevelFunctions = FillLevelFunction.<RunningModeBehaviour> create(0)
                                                                                          .add(5000,
                                                                                               new RunningModeBehaviour(0.3968,
                                                                                                                        CommodityMeasurables.create()
                                                                                                                                            .electricity(toWatt(0))
                                                                                                                                            .build(),
                                                                                                                        toEuroPerHour(0)))
                                                                                          .add(6000,
                                                                                               new RunningModeBehaviour(0.2778,
                                                                                                                        CommodityMeasurables.create()
                                                                                                                                            .electricity(toWatt(0))
                                                                                                                                            .build(),
                                                                                                                        toEuroPerHour(0)))
                                                                                          .build();

        FillLevelFunction<RunningModeBehaviour> chargeFillLevelFunctions = FillLevelFunction.<RunningModeBehaviour> create(0)
                                                                                            .add(5000,
                                                                                                 new RunningModeBehaviour(0.3968,
                                                                                                                          CommodityMeasurables.create()
                                                                                                                                              .electricity(toWatt(1460))
                                                                                                                                              .build(),
                                                                                                                          toEuroPerHour(0)))
                                                                                            .add(6000,
                                                                                                 new RunningModeBehaviour(0.2778,
                                                                                                                          CommodityMeasurables.create()
                                                                                                                                              .electricity(toWatt(1050))
                                                                                                                                              .build(),
                                                                                                                          toEuroPerHour(0)))
                                                                                            .build();
        FillLevelFunction<RunningModeBehaviour> dischargeFillLevelFunctions = FillLevelFunction.<RunningModeBehaviour> create(0)
                                                                                               .add(5000,
                                                                                                    new RunningModeBehaviour(-0.3968,
                                                                                                                             CommodityMeasurables.create()
                                                                                                                                                 .electricity(toWatt(-1400))
                                                                                                                                                 .build(),
                                                                                                                             toEuroPerHour(0)))
                                                                                               .add(6000,
                                                                                                    new RunningModeBehaviour(-0.3968,
                                                                                                                             CommodityMeasurables.create()
                                                                                                                                                 .electricity(toWatt(-1400))
                                                                                                                                                 .build(),
                                                                                                                             toEuroPerHour(0)))
                                                                                               .build();

        RunningMode<FillLevelFunction<RunningModeBehaviour>> idleRunningMode =
                                                                               new RunningMode<FillLevelFunction<RunningModeBehaviour>>(BatteryMode.IDLE.ordinal(),
                                                                                                                                        "idle",
                                                                                                                                        idleFillLevelFunctions,
                                                                                                                                        idleTransition);

        RunningMode<FillLevelFunction<RunningModeBehaviour>> chargeRunningMode =
                                                                                 new RunningMode<FillLevelFunction<RunningModeBehaviour>>(BatteryMode.CHARGE.ordinal(),
                                                                                                                                          "charging",
                                                                                                                                          chargeFillLevelFunctions,
                                                                                                                                          chargeTransition);
        RunningMode<FillLevelFunction<RunningModeBehaviour>> dischargeRunningMode =
                                                                                    new RunningMode<FillLevelFunction<RunningModeBehaviour>>(BatteryMode.DISCHARGE.ordinal(),
                                                                                                                                             "discharging",
                                                                                                                                             dischargeFillLevelFunctions,
                                                                                                                                             dischargeTransition);

        return ActuatorBehaviour.create(actuatorId)
                                .add(idleRunningMode)
                                .add(chargeRunningMode)
                                .add(dischargeRunningMode)
                                .build();
    }

    /**
     * Make a transistion with no timers;
     *
     * @param transitionId
     * @return
     */
    private Transition makeTransition(int transitionId) {
        Set<Timer> startTimers = null;
        Set<Timer> blockingTimers = null;
        Measurable<Money> transitionCosts = toEurocent(0);
        Measurable<Duration> transitionTime = toSeconds(0);
        return new Transition(transitionId,
                              startTimers,
                              blockingTimers,
                              transitionCosts,
                              transitionTime);
    }

    /**
     * Create the battery actuator set, based on the battery mode
     *
     * @param batteryMode
     * @return
     */
    private Set<ActuatorUpdate> makeBatteryActuatorUpdateSet(int actuatorId, BatteryMode batteryMode) {
        Set<ActuatorUpdate> runningModes = new HashSet<ActuatorUpdate>();
        ActuatorUpdate actuatorUpdate = new ActuatorUpdate(actuatorId, batteryMode.ordinal(), null);
        runningModes.add(actuatorUpdate);
        return runningModes;
    }

    /**
     * current fill level is calculated by multiplying the total capacity with the state of charge.
     *
     * @param batteryState
     * @return
     */
    private Measure<Double, Energy> getCurrentFillLevel(BatteryState batteryState) {
        double stateOfCharge = batteryState.getStateOfCharge();
        Measurable<Energy> totalCapacity = batteryState.getTotalCapacity();
        return Measure.valueOf(stateOfCharge * totalCapacity.doubleValue(WH),
                               WH);
    }

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    @Reference
    public void setScheduledExecutorService(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    // ---------------- helper conversion methodes ------------------

    private Measure<Integer, Power> toWatt(int watt) {
        return Measure.valueOf(watt, SI.WATT);
    }

    private Measure<Integer, Money> toEurocent(int eurocent) {
        return Measure.valueOf(eurocent, NonSI.EUROCENT);
    }

    private Measurable<MoneyFlow> toEuroPerHour(int euroPerHour) {
        return Measure.valueOf(euroPerHour,
                               NonSI.EUR_PER_HOUR);
    }

    private Measure<Integer, Duration> toSeconds(int seconds) {
        return Measure.valueOf(seconds, SI.SECOND);
    }

}
