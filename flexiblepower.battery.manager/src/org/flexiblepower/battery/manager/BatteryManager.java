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
    private static final int BATTERY_ACTUATOR_ID = 1; // self chosen id for the actuator

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "BatteryManager", description = "Unique resourceID")
        String resourceId();
    }

    private Config configuration;
    private TimeService timeService;
    private ScheduledExecutorService scheduler;
    private BatteryState currentBatteryState;
    private Date changedStateTimestamp;
    Actuator batteryActuator;
    private BufferRegistration<Energy> batteryBufferRegistration;

    /**
     * Handle the start of the registration request.
     */
    @Override
    protected List<? extends ResourceMessage> startRegistration(BatteryState batteryState) {
        // safe current state of the battery
        currentBatteryState = batteryState;
        changedStateTimestamp = timeService.getTime();

        // ---- Buffer registration ----
        // create a battery actuator for electricity
        batteryActuator = new Actuator(BATTERY_ACTUATOR_ID,
                                       "Battery",
                                       CommoditySet.onlyElectricity);
        // create a buffer registration message
        Set<Actuator> actuators = new HashSet<Actuator>();
        actuators.add(batteryActuator);
        batteryBufferRegistration = new BufferRegistration<Energy>(configuration.resourceId(),
                                                                   changedStateTimestamp,
                                                                   toSeconds(0),
                                                                   "Battery level",
                                                                   WH,
                                                                   actuators);

        // ---- Buffer system description ----
        // create a behavior of the battery
        ActuatorBehaviour batteryActuatorBehaviour = makeBatteryActuatorBehaviour(batteryActuator.getActuatorId());
        // create the leakage function of the battery
        FillLevelFunction<LeakageRate> bufferLeakageFunction = FillLevelFunction.<LeakageRate> create(0)
                                                                                .add(6000, new LeakageRate(0.0001))
                                                                                .build();
        // create the buffer system description message
        Set<ActuatorBehaviour> actuatorsBehaviours = new HashSet<ActuatorBehaviour>();
        actuatorsBehaviours.add(batteryActuatorBehaviour);
        BufferSystemDescription sysDescr = new BufferSystemDescription(batteryBufferRegistration,
                                                                       changedStateTimestamp,
                                                                       changedStateTimestamp,
                                                                       actuatorsBehaviours,
                                                                       bufferLeakageFunction);

        // ---- Buffer state update ----
        // create running mode
        Set<ActuatorUpdate> currentRunningMode = makeBatteryRunningModes(batteryActuator.getActuatorId(),
                                                                         batteryState.getCurrentMode());
        // create buffer state update message
        Measure<Double, Energy> currentFillLevel = getCurrentFillLevel(batteryState);
        BufferStateUpdate<Energy> update = new BufferStateUpdate<Energy>(batteryBufferRegistration,
                                                                         changedStateTimestamp,
                                                                         changedStateTimestamp,
                                                                         currentFillLevel,
                                                                         currentRunningMode);

        log.debug("Battery manager start registration completed.");
        // return the three messages
        return Arrays.asList(batteryBufferRegistration, sysDescr, update);
    }

    /**
     * Handle the update state request.
     */
    @SuppressWarnings("unchecked")
    @Override
    protected List<? extends ResourceMessage> updatedState(BatteryState batteryState) {
        log.debug("Receive battery state update, mode=" + batteryState.getCurrentMode());

        // Do nothing if there is no useful battery state change request
        if (batteryState.equals(currentBatteryState)) {
            return Collections.emptyList();
        } else { // battery state change request
            // save previous state
            currentBatteryState = batteryState;
            changedStateTimestamp = timeService.getTime();

            // this state change is immediately valid
            Date validFrom = changedStateTimestamp;

            // create running mode
            Set<ActuatorUpdate> currentRunningMode = makeBatteryRunningModes(batteryActuator.getActuatorId(),
                                                                             batteryState.getCurrentMode());
            // create buffer state update message
            Measure<Double, Energy> currentFillLevel = getCurrentFillLevel(batteryState);
            BufferStateUpdate<Energy> update = new BufferStateUpdate<Energy>(batteryBufferRegistration,
                                                                             changedStateTimestamp,
                                                                             validFrom,
                                                                             currentFillLevel,
                                                                             currentRunningMode);
            // return state update message
            return Arrays.asList(update);
        }
    }

    /**
     * Handle a received allocation from the energy app.
     */
    @Override
    protected BatteryControlParameters receivedAllocation(ResourceMessage message) {
        // is the message a buffer allocation?
        if (message instanceof BufferAllocation) {
            BufferAllocation bufferAllocation = (BufferAllocation) message;
            log.debug("Received allocation " + bufferAllocation);

            // process all actuator allocations
            Set<ActuatorAllocation> allocations = bufferAllocation.getActuatorAllocations();
            for (ActuatorAllocation allocation : allocations) {
                // determine battery mode and specified delay.
                BatteryMode batteryMode = BatteryMode.values()[allocation.getRunningModeId()];
                long delay = allocation.getStartTime().getTime() - timeService.getCurrentTimeMillis();
                delay = (delay <= 0 ? 1 : delay);

                // set up the switch to the battery mode after the specified delay
                scheduleSwitchToBatteryMode(delay, batteryMode);

                // Send to attached energy app the acceptance of the allocation request
                allocationStatusUpdate(new AllocationStatusUpdate(timeService.getTime(),
                                                                  bufferAllocation,
                                                                  AllocationStatus.ACCEPTED,
                                                                  ""));
            }

            // all battery mode changes are scheduled at time, so nothing to return
            return null;
        } else {
            log.warn("Unexpected resource (" + message.toString() + ") message type (" + message.getClass().getName()
                     + ")received");
            return null;
        }
    }

    /**
     * Set up the change to the battery mode, after the specified delay
     *
     * @param delay
     *            Delay in ms
     * @param newBatteryMode
     *            The battery mode to switch to after the delay
     */
    private void scheduleSwitchToBatteryMode(final long delay, final BatteryMode newBatteryMode) {
        final Runnable allocationHelper = new Runnable() {
            @Override
            public void run() {
                sendControlParameters(new BatteryControlParameters() {
                    @Override
                    public BatteryMode getMode() {
                        return newBatteryMode;
                    }
                });
                log.debug("Has set up allocation at " + delay + "ms for batteryMode=" + newBatteryMode);
            }
        };
        scheduler.schedule(allocationHelper, delay, TimeUnit.MILLISECONDS);
    }

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        // create a configuration
        configuration = Configurable.createConfigurable(Config.class, properties);
        log.debug("BatteryManager Activated");
    }

    @Deactivate
    public void deactivate() {
    }

    // ---------------------------- helper classes -----------------------------

    /**
     * Helper method to set up everything for the battery actuator behavior specification
     *
     * @param actuatorId
     * @return Returns the completely filled battery actuator behavior object
     */
    private ActuatorBehaviour makeBatteryActuatorBehaviour(int actuatorId) {
        // make three transitions holders
        Transition toChargingTransition = makeTransition(0);
        Transition toIdleTransition = makeTransition(1);
        Transition toDisChargingTransition = makeTransition(2);

        // create the transition graph, it is fully connected in this case
        Set<Transition> idleTransition = new HashSet<Transition>();
        idleTransition.add(toChargingTransition);
        idleTransition.add(toDisChargingTransition);
        Set<Transition> chargeTransition = new HashSet<Transition>();
        chargeTransition.add(toIdleTransition);
        chargeTransition.add(toDisChargingTransition);
        Set<Transition> dischargeTransition = new HashSet<Transition>();
        dischargeTransition.add(toIdleTransition);
        dischargeTransition.add(toChargingTransition);

        // Make the three running battery modes (idle, charge, discharge), all with zero cost
        // -----------------------------------------------------------------------------------
        //
        // ___________CHARGE_____________________________IDLE_______________________________DISCHARGE
        // Range(X)___Chargespeed (x/s)__Elec power (W)__Chargespeed (x/s)__Elec power (W)__Dischargespeed (x/s)__power
        // 0-5000_____0.3968_____________1460____________0__________________0_______________-0.3968_______________-1400
        // 5000-6000__0.2778_____________1050____________0__________________0_______________-0.3968_______________-1400

        // make the fill level functions
        // CHARGE
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
        // IDLE
        FillLevelFunction<RunningModeBehaviour> idleFillLevelFunctions = FillLevelFunction.<RunningModeBehaviour> create(0)
                                                                                          .add(5000,
                                                                                               new RunningModeBehaviour(0,
                                                                                                                        CommodityMeasurables.create()
                                                                                                                                            .electricity(toWatt(0))
                                                                                                                                            .build(),
                                                                                                                        toEuroPerHour(0)))
                                                                                          .add(6000,
                                                                                               new RunningModeBehaviour(0,
                                                                                                                        CommodityMeasurables.create()
                                                                                                                                            .electricity(toWatt(0))
                                                                                                                                            .build(),
                                                                                                                        toEuroPerHour(0)))
                                                                                          .build();

        // DISCHARGE
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

        // Based on the fill level functions and the transitions, create the three running modes
        RunningMode<FillLevelFunction<RunningModeBehaviour>> chargeRunningMode =
                                                                                 new RunningMode<FillLevelFunction<RunningModeBehaviour>>(BatteryMode.CHARGE.ordinal(),
                                                                                                                                          "charging",
                                                                                                                                          chargeFillLevelFunctions,
                                                                                                                                          chargeTransition);
        RunningMode<FillLevelFunction<RunningModeBehaviour>> idleRunningMode =
                                                                               new RunningMode<FillLevelFunction<RunningModeBehaviour>>(BatteryMode.IDLE.ordinal(),
                                                                                                                                        "idle",
                                                                                                                                        idleFillLevelFunctions,
                                                                                                                                        idleTransition);
        RunningMode<FillLevelFunction<RunningModeBehaviour>> dischargeRunningMode =
                                                                                    new RunningMode<FillLevelFunction<RunningModeBehaviour>>(BatteryMode.DISCHARGE.ordinal(),
                                                                                                                                             "discharging",
                                                                                                                                             dischargeFillLevelFunctions,
                                                                                                                                             dischargeTransition);

        // return the actuator behavior with the three running modes for the specified actuator id
        return ActuatorBehaviour.create(actuatorId)
                                .add(idleRunningMode)
                                .add(chargeRunningMode)
                                .add(dischargeRunningMode)
                                .build();
    }

    /**
     * Make a transition with no timers, no cost and the specified transition id
     *
     * @param transitionId
     * @return
     */
    private Transition makeTransition(int transitionId) {
        // no timers
        Set<Timer> startTimers = null;
        Set<Timer> blockingTimers = null;
        Measurable<Duration> transitionTime = toSeconds(0);

        // no cost
        Measurable<Money> transitionCosts = toEurocent(0);

        // return transition
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
    private Set<ActuatorUpdate> makeBatteryRunningModes(int actuatorId, BatteryMode batteryMode) {
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
