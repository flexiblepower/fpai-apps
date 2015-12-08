package org.flexiblepower.battery.manager;

import static javax.measure.unit.SI.JOULE;
import static javax.measure.unit.SI.SECOND;
import static javax.measure.unit.SI.WATT;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Money;
import javax.measure.quantity.Power;
import javax.measure.unit.AlternateUnit;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.flexiblepower.battery.manager.BatteryManager.Config;
import org.flexiblepower.context.FlexiblePowerContext;
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
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.messaging.Ports;
import org.flexiblepower.ral.drivers.battery.BatteryControlParameters;
import org.flexiblepower.ral.drivers.battery.BatteryMode;
import org.flexiblepower.ral.drivers.battery.BatteryState;
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.ral.messages.AllocationStatus;
import org.flexiblepower.ral.messages.AllocationStatusUpdate;
import org.flexiblepower.ral.messages.ControlSpaceRevoke;
import org.flexiblepower.ral.messages.ResourceMessage;
import org.flexiblepower.ral.values.CommodityMeasurables;
import org.flexiblepower.ral.values.CommoditySet;
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
@Ports(@Port(name = "driver", sends = BatteryControlParameters.class, accepts = BatteryState.class))
public class BatteryManager extends AbstractResourceManager<BatteryState, BatteryControlParameters>
                            implements
                            BufferResourceManager {
    private static final int MINIMUM_BUFFER_LEVEL = 0;
    private static final AlternateUnit<Energy> UNIT_JOULE = JOULE;
    private static final Unit<Duration> MS = SI.MILLI(SECOND);
    private static final Logger logger = LoggerFactory.getLogger(BatteryManager.class);

    private static final int BATTERY_ACTUATOR_ID = 1; // self chosen id for the actuator
    private static final double CONSUMPTION_WHEN_IDLE_IN_WATTS = 0;

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "BatteryManager", description = "Unique resourceID")
               String resourceId();
    }

    private Config configuration;
    private FlexiblePowerContext context;
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
        changedStateTimestamp = context.currentTime();

        // ---- Buffer registration ----
        // create a battery actuator for electricity
        batteryActuator = new Actuator(BATTERY_ACTUATOR_ID,
                                       "Battery",
                                       CommoditySet.onlyElectricity);
        // create a buffer registration message
        batteryBufferRegistration = new BufferRegistration<Energy>(configuration.resourceId(),
                                                                   changedStateTimestamp,
                                                                   Measure.zero(SECOND),
                                                                   "Battery level",
                                                                   UNIT_JOULE,
                                                                   Collections.<Actuator> singleton(batteryActuator));

        // ---- Buffer system description ----
        // create a behavior of the battery
        ActuatorBehaviour batteryActuatorBehaviour = makeBatteryActuatorBehaviour(batteryActuator.getActuatorId(),
                                                                                  batteryState);
        // create the leakage function of the battery
        FillLevelFunction<LeakageRate> bufferLeakageFunction = FillLevelFunction.<LeakageRate> create(MINIMUM_BUFFER_LEVEL)
                                                                                .add(batteryState.getTotalCapacity()
                                                                                                 .doubleValue(UNIT_JOULE),
                                                                                     new LeakageRate(batteryState.getSelfDischargeSpeed()
                                                                                                                 .doubleValue(WATT)))
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

        logger.debug("Battery manager start registration completed.");
        // return the three messages
        return Arrays.asList(batteryBufferRegistration, sysDescr, update);
    }

    /**
     * Handle the update state request.
     */
    @SuppressWarnings("unchecked")
    @Override
    protected List<? extends ResourceMessage> updatedState(BatteryState batteryState) {
        logger.debug("Receive battery state update, mode=" + batteryState.getCurrentMode());

        // Do nothing if there is no useful battery state change request
        if (batteryState.equals(currentBatteryState)) {
            return Collections.emptyList();
        } else { // battery state change request
            // save previous state
            currentBatteryState = batteryState;
            changedStateTimestamp = context.currentTime();

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
            logger.debug("Received allocation " + bufferAllocation);

            // process all actuator allocations
            Set<ActuatorAllocation> allocations = bufferAllocation.getActuatorAllocations();
            for (ActuatorAllocation allocation : allocations) {
                // determine battery mode and specified delay.
                BatteryMode batteryMode = BatteryMode.values()[allocation.getRunningModeId()];
                long delay = allocation.getStartTime().getTime() - context.currentTimeMillis();
                delay = (delay <= 0 ? 1 : delay);

                // set up the switch to the battery mode after the specified delay
                scheduleSwitchToBatteryMode(Measure.valueOf(delay, MS), batteryMode);

                // Send to attached energy app the acceptance of the allocation request
                allocationStatusUpdate(new AllocationStatusUpdate(context.currentTime(),
                                                                  bufferAllocation,
                                                                  AllocationStatus.ACCEPTED,
                                                                  ""));
            }

            // all battery mode changes are scheduled at time, so nothing to return
            return null;
        } else {
            logger.warn("Unexpected resource (" + message.toString()
                        + ") message type ("
                        + message.getClass().getName()
                        + ")received");
            return null;
        }
    }

    @Override
    protected ControlSpaceRevoke createRevokeMessage() {
        return new ControlSpaceRevoke(configuration.resourceId(), context.currentTime());
    }

    /**
     * Set up the change to the battery mode, after the specified delay
     *
     * @param delay
     *            Delay in ms
     * @param newBatteryMode
     *            The battery mode to switch to after the delay
     */
    private void scheduleSwitchToBatteryMode(final Measurable<Duration> delay, final BatteryMode newBatteryMode) {
        final Runnable allocationHelper = new Runnable() {
            @Override
            public void run() {
                sendControlParameters(new BatteryControlParameters() {
                    @Override
                    public BatteryMode getMode() {
                        return newBatteryMode;
                    }
                });
                logger.debug("Has set up allocation at " + delay + " for batteryMode=" + newBatteryMode);
            }
        };
        context.schedule(allocationHelper, delay);
    }

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        // create a configuration
        configuration = Configurable.createConfigurable(Config.class, properties);
        logger.debug("BatteryManager Activated");
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
    private ActuatorBehaviour makeBatteryActuatorBehaviour(int actuatorId, BatteryState state) {
        // make three transitions holders
        Transition toChargingTransition = makeTransition(BatteryMode.CHARGE.ordinal());
        Transition toIdleTransition = makeTransition(BatteryMode.IDLE.ordinal());
        Transition toDisChargingTransition = makeTransition(BatteryMode.DISCHARGE.ordinal());

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

        FillLevelFunction<RunningModeBehaviour> chargeFillLevelFunctions, idleFillLevelFunctions,
                dischargeFillLevelFunctions;

        double totalCapacity = state.getTotalCapacity().doubleValue(UNIT_JOULE);
        Measurable<Power> chargeSpeed = state.getChargeSpeed();

        chargeFillLevelFunctions = FillLevelFunction.<RunningModeBehaviour> create(MINIMUM_BUFFER_LEVEL)
                                                    .add(totalCapacity,
                                                         new RunningModeBehaviour(chargeSpeed.doubleValue(WATT)
                                                                                  * state.getChargeEfficiency(),
                                                                                  CommodityMeasurables.electricity(chargeSpeed),
                                                                                  Measure.zero(NonSI.EUR_PER_HOUR)))
                                                    .build();

        idleFillLevelFunctions = FillLevelFunction.<RunningModeBehaviour> create(MINIMUM_BUFFER_LEVEL)
                                                  .add(totalCapacity,
                                                       new RunningModeBehaviour(CONSUMPTION_WHEN_IDLE_IN_WATTS,
                                                                                CommodityMeasurables.electricity(Measure.zero(WATT)),
                                                                                Measure.zero(NonSI.EUR_PER_HOUR)))
                                                  .build();

        Measurable<Power> dischargeSpeed = state.getDischargeSpeed();
        // TODO: Check if discharge speed is the fill level change and the discharge/efficiency is correct...
        dischargeFillLevelFunctions = FillLevelFunction.<RunningModeBehaviour> create(MINIMUM_BUFFER_LEVEL)
                                                       .add(totalCapacity,
                                                            new RunningModeBehaviour(-dischargeSpeed.doubleValue(WATT)
                                                                                     / state.getDischargeEfficiency(),
                                                                                     CommodityMeasurables.electricity(Measure.valueOf(-dischargeSpeed.doubleValue(WATT),
                                                                                                                                      WATT)),
                                                                                     Measure.zero(NonSI.EUR_PER_HOUR)))
                                                       .build();

        // Based on the fill level functions and the transitions, create the three running modes
        RunningMode<FillLevelFunction<RunningModeBehaviour>> chargeRunningMode = new RunningMode<FillLevelFunction<RunningModeBehaviour>>(BatteryMode.CHARGE.ordinal(),
                                                                                                                                          "charging",
                                                                                                                                          chargeFillLevelFunctions,
                                                                                                                                          chargeTransition);
        RunningMode<FillLevelFunction<RunningModeBehaviour>> idleRunningMode = new RunningMode<FillLevelFunction<RunningModeBehaviour>>(BatteryMode.IDLE.ordinal(),
                                                                                                                                        "idle",
                                                                                                                                        idleFillLevelFunctions,
                                                                                                                                        idleTransition);
        RunningMode<FillLevelFunction<RunningModeBehaviour>> dischargeRunningMode = new RunningMode<FillLevelFunction<RunningModeBehaviour>>(BatteryMode.DISCHARGE.ordinal(),
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
        Measurable<Duration> transitionTime = Measure.zero(SECOND);

        // no cost
        Measurable<Money> transitionCosts = Measure.zero(NonSI.EUROCENT);

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
        return Collections.<ActuatorUpdate> singleton(new ActuatorUpdate(actuatorId, batteryMode.ordinal(), null));
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
        return Measure.valueOf(stateOfCharge * totalCapacity.doubleValue(UNIT_JOULE), UNIT_JOULE);
    }

    @Reference
    public void setContext(FlexiblePowerContext context) {
        this.context = context;
    }
}
