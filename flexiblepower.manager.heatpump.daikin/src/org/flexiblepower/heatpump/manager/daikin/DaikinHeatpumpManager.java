package org.flexiblepower.heatpump.manager.daikin;

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
import javax.measure.quantity.Money;
import javax.measure.quantity.Temperature;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

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
import org.flexiblepower.heatpump.manager.daikin.DaikinHeatpumpManager.Config;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.messaging.Ports;
import org.flexiblepower.ral.drivers.heatpump.HeatpumpControlParameters;
import org.flexiblepower.ral.drivers.heatpump.HeatpumpState;
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

/**
 *
 * @author bafkrstulovic
 *
 */

@Component(designateFactory = Config.class, provide = Endpoint.class, immediate = true)
@Ports(@Port(name = "driver", sends = HeatpumpControlParameters.class, accepts = HeatpumpState.class))
public class DaikinHeatpumpManager extends
                                   AbstractResourceManager<HeatpumpState, HeatpumpControlParameters>
                                   implements
                                   BufferResourceManager {
    private static final Unit<Duration> MS = SI.MILLI(SECOND);
    private static final Logger logger = LoggerFactory.getLogger(DaikinHeatpumpManager.class);

    private static final int HEATPUMP_ACTUATOR_ID = 1; // self chosen id for the actuator
    private static final int RUNNINGMODE_IDLE = 0; // self chosen id for running mode idle
    private static final int RUNNINGMODE_HEAT = 1; // self chosen id for the running mode charging

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "DaikinHeatpumpManager", description = "Unique resourceID")
               String resourceId();

        @Meta.AD(deflt = "18", description = "Minimum Temperature in C")
               double minimumTemperature();

        @Meta.AD(deflt = "20", description = "Maximum Temperature in C")
               double maximumTemperature();

        @Meta.AD(deflt = "0.02", description = "Fill rate in C per second")
               double fillRate();

        @Meta.AD(deflt = "-0.01", description = "Self discharge in C per second")
               double selfDischarge();

        @Meta.AD(deflt = "300", description = "Consumption in Watt")
            int consumption();
    }

    private Config config;
    private FlexiblePowerContext context;
    private HeatpumpState currentHeatpumpState;
    private Date changedStateTimestamp;
    Actuator heatpumpActuator;
    private BufferRegistration<Temperature> heatpumpBufferRegistration;

    /**
     * Handle the start of the registration request.
     */
    @Override
    protected List<? extends ResourceMessage> startRegistration(HeatpumpState heatpumpState) {
        // safe current state of the refrigerator
        currentHeatpumpState = heatpumpState;
        changedStateTimestamp = context.currentTime();

        // ---- Buffer registration ----
        // create a refrigerator actuator for electricity
        heatpumpActuator = new Actuator(HEATPUMP_ACTUATOR_ID,
                                        "Daikin Heatpump",
                                        CommoditySet.onlyElectricity);

        // create a buffer registration message
        Set<Actuator> actuators = new HashSet<Actuator>();
        actuators.add(heatpumpActuator);
        heatpumpBufferRegistration = new BufferRegistration<Temperature>(config.resourceId(),
                                                                         changedStateTimestamp,
                                                                         Measure.zero(SECOND),
                                                                         "Temperature level",
                                                                         SI.CELSIUS,
                                                                         actuators);

        // ---- Buffer system description ----
        // create a behavior of the refrigerator
        ActuatorBehaviour heatpumpActuatorBehaviour = makeHeatpumpActuatorBehaviour(heatpumpActuator.getActuatorId(),
                                                                                    heatpumpState);

        // create the leakage function of the refrigerator
        FillLevelFunction<LeakageRate> bufferLeakageFunction = FillLevelFunction.<LeakageRate> create(config.minimumTemperature())
                                                                                .add(config.maximumTemperature(),
                                                                                     new LeakageRate(config.selfDischarge()))
                                                                                .build();

        // create the buffer system description message
        Set<ActuatorBehaviour> actuatorsBehaviours = new HashSet<ActuatorBehaviour>();
        actuatorsBehaviours.add(heatpumpActuatorBehaviour);
        BufferSystemDescription sysDescr = new BufferSystemDescription(heatpumpBufferRegistration,
                                                                       changedStateTimestamp,
                                                                       changedStateTimestamp,
                                                                       actuatorsBehaviours,
                                                                       bufferLeakageFunction);

        // ---- Buffer state update ----
        // create running mode
        Set<ActuatorUpdate> currentRunningMode = makeHeatpumpRunningModes(heatpumpActuator.getActuatorId(),
                                                                          heatpumpState.getHeatMode());
        // create buffer state update message
        BufferStateUpdate<Temperature> update = new BufferStateUpdate<Temperature>(heatpumpBufferRegistration,
                                                                                   changedStateTimestamp,
                                                                                   changedStateTimestamp,
                                                                                   heatpumpState.getCurrentTemperature(),
                                                                                   currentRunningMode);

        logger.debug("heatpump manager start registration completed.");
        // return the three messages
        return Arrays.asList(heatpumpBufferRegistration, sysDescr, update);
    }

    /**
     * Handle the update state request.
     */
    @SuppressWarnings("unchecked")
    @Override
    protected List<? extends ResourceMessage> updatedState(HeatpumpState heatpumpState) {
        logger.debug("Receive refrigerator state update, supercooling= " + heatpumpState.getHeatMode());

        // Do nothing if there is no useful refrigerator state change request
        if (heatpumpState.equals(currentHeatpumpState)) {
            return Collections.emptyList();
        } else { // refrigerator state change request
            // save previous state
            currentHeatpumpState = heatpumpState;
            changedStateTimestamp = context.currentTime();

            // this state change is immediately valid
            Date validFrom = changedStateTimestamp;

            // create running mode
            Set<ActuatorUpdate> currentRunningMode = makeHeatpumpRunningModes(heatpumpActuator.getActuatorId(),
                                                                              heatpumpState.getHeatMode());
            // create buffer state update message
            BufferStateUpdate<Temperature> update = new BufferStateUpdate<Temperature>(heatpumpBufferRegistration,
                                                                                       changedStateTimestamp,
                                                                                       validFrom,
                                                                                       heatpumpState.getCurrentTemperature(),
                                                                                       currentRunningMode);
            // return state update message
            return Arrays.asList(update);
        }
    }

    /**
     * Handle a received allocation from the energy app.
     */
    @Override
    protected HeatpumpControlParameters receivedAllocation(ResourceMessage message) {
        // is the message a buffer allocation?
        if (message instanceof BufferAllocation) {
            BufferAllocation bufferAllocation = (BufferAllocation) message;
            logger.debug("Received allocation " + bufferAllocation);

            // process all actuator allocations
            Set<ActuatorAllocation> allocations = bufferAllocation.getActuatorAllocations();
            for (ActuatorAllocation allocation : allocations) {

                // Derive energy app's decision
                boolean heat;
                if (allocation.getRunningModeId() == RUNNINGMODE_HEAT) {
                    heat = true;
                } else {
                    heat = false;
                }

                // determine refrigerator mode and specified delay.
                long delay = allocation.getStartTime().getTime() - context.currentTimeMillis();
                delay = (delay <= 0 ? 1 : delay);

                // set up the switch to the refrigerator mode after the specified delay
                scheduleSwitchToHeatingMode(Measure.valueOf(delay, MS), heat);

                // Send to attached energy app the acceptance of the allocation request
                allocationStatusUpdate(new AllocationStatusUpdate(context.currentTime(),
                                                                  bufferAllocation,
                                                                  AllocationStatus.ACCEPTED,
                                                                  ""));
            }

            // all refrigerator mode changes are scheduled at time, so nothing to return
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
        return new ControlSpaceRevoke(config.resourceId(), context.currentTime());
    }

    /**
     * Set up the change to the refrigerator mode, after the specified delay
     *
     * @param delay
     *            Delay in ms
     * @param newrefrigeratorMode
     *            The refrigerator mode to switch to after the delay
     */
    private void scheduleSwitchToHeatingMode(final Measurable<Duration> delay, final boolean isHeating) {
        final Runnable allocationHelper = new Runnable() {
            @Override
            public void run() {
                sendControlParameters(new HeatpumpControlParameters() {

                    @Override
                    public boolean getHeatMode() {
                        return isHeating;
                    }
                });
                logger.debug("Has set up allocation at " + delay + " for heating mode=" + isHeating);
            }
        };
        context.schedule(allocationHelper, delay);
    }

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        // create a configuration
        config = Configurable.createConfigurable(Config.class, properties);
        logger.debug("HeatpumpManager Activated");
    }

    @Deactivate
    public void deactivate() {
    }

    // ---------------------------- helper classes -----------------------------

    /**
     * Helper method to set up everything for the refrigerator actuator behavior specification
     *
     * @param actuatorId
     * @return Returns the completely filled refrigerator actuator behavior object
     */
    private ActuatorBehaviour makeHeatpumpActuatorBehaviour(int actuatorId, HeatpumpState state) {
        // make three transitions holders
        Transition toChargingTransition = makeTransition(RUNNINGMODE_HEAT);
        Transition toIdleTransition = makeTransition(RUNNINGMODE_IDLE);

        // create the transition graph, it is fully connected in this case
        Set<Transition> idleTransition = new HashSet<Transition>();
        idleTransition.add(toChargingTransition);
        Set<Transition> chargeTransition = new HashSet<Transition>();
        chargeTransition.add(toIdleTransition);

        FillLevelFunction<RunningModeBehaviour> chargeFillLevelFunctions, idleFillLevelFunctions,
                dischargeFillLevelFunctions;

        chargeFillLevelFunctions = FillLevelFunction.<RunningModeBehaviour> create(config.minimumTemperature())
                                                    .add(config.maximumTemperature(),
                                                         new RunningModeBehaviour(config.fillRate(),
                                                                                  CommodityMeasurables.electricity(Measure.valueOf(config.consumption(),
                                                                                                                                   WATT)),
                                                                                  Measure.zero(NonSI.EUR_PER_HOUR)))
                                                    .build();

        idleFillLevelFunctions = FillLevelFunction.<RunningModeBehaviour> create(config.minimumTemperature())
                                                  .add(config.maximumTemperature(),
                                                       new RunningModeBehaviour(0,
                                                                                CommodityMeasurables.electricity(Measure.zero(WATT)),
                                                                                Measure.zero(NonSI.EUR_PER_HOUR)))
                                                  .build();

        // Based on the fill level functions and the transitions, create the two running modes
        RunningMode<FillLevelFunction<RunningModeBehaviour>> chargeRunningMode = new RunningMode<FillLevelFunction<RunningModeBehaviour>>(RUNNINGMODE_HEAT,
                                                                                                                                          "charging",
                                                                                                                                          chargeFillLevelFunctions,
                                                                                                                                          chargeTransition);
        RunningMode<FillLevelFunction<RunningModeBehaviour>> idleRunningMode = new RunningMode<FillLevelFunction<RunningModeBehaviour>>(RUNNINGMODE_IDLE,
                                                                                                                                        "idle",
                                                                                                                                        idleFillLevelFunctions,
                                                                                                                                        idleTransition);

        // return the actuator behavior with the three running modes for the specified actuator id
        return ActuatorBehaviour.create(actuatorId)
                                .add(idleRunningMode)
                                .add(chargeRunningMode)
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
     * Create the heatpump actuator set, based on the heatpump mode
     *
     * @param heatpumpMode
     * @return
     */
    private Set<ActuatorUpdate> makeHeatpumpRunningModes(int actuatorId, boolean heatingMode) {
        Set<ActuatorUpdate> runningModes = new HashSet<ActuatorUpdate>();
        ActuatorUpdate actuatorUpdate;
        if (heatingMode) {
            actuatorUpdate = new ActuatorUpdate(actuatorId, RUNNINGMODE_HEAT, null);
        } else {
            actuatorUpdate = new ActuatorUpdate(actuatorId, RUNNINGMODE_IDLE, null);

        }
        runningModes.add(actuatorUpdate);
        return runningModes;
    }

    @Reference
    public void setContext(FlexiblePowerContext context) {
        this.context = context;
    }
}
