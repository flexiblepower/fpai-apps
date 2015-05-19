package org.flexiblepower.refrigerator.manager.miele;

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
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.messaging.Ports;
import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorControlParameters;
import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorState;
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.ral.messages.AllocationStatus;
import org.flexiblepower.ral.messages.AllocationStatusUpdate;
import org.flexiblepower.ral.messages.ControlSpaceRevoke;
import org.flexiblepower.ral.messages.ResourceMessage;
import org.flexiblepower.ral.values.CommodityMeasurables;
import org.flexiblepower.ral.values.CommoditySet;
import org.flexiblepower.refrigerator.manager.miele.MieleRefrigeratorManager.Config;
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
@Ports(@Port(name = "driver", sends = RefrigeratorControlParameters.class, accepts = RefrigeratorState.class))
public class MieleRefrigeratorManager extends
                                     AbstractResourceManager<RefrigeratorState, RefrigeratorControlParameters>
                                                                                                              implements
                                                                                                              BufferResourceManager {
    private static final Unit<Duration> MS = SI.MILLI(SECOND);
    private static final Logger logger = LoggerFactory.getLogger(MieleRefrigeratorManager.class);

    private static final int REFRIGIRATOR_ACTUATOR_ID = 1; // self chosen id for the actuator
    private static final int RUNNINGMODE_IDLE = 0; // self chosen id for running mode idle
    private static final int RUNNINGMODE_SUPERCOOL = 1; // self chosen id for the running mode charging

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "MieleRefrigeratorManager", description = "Unique resourceID")
        String resourceId();

        @Meta.AD(deflt = "3", description = "Minimum Temperature in C")
        double minimumTemperature();

        @Meta.AD(deflt = "8", description = "Maximum Temperature in C")
        double maximumTemperature();

        @Meta.AD(deflt = "0.01", description = "Self discharege in C per second")
        double selfDischarge();
    }

    private Config config;
    private FlexiblePowerContext context;
    private RefrigeratorState currentRefrigeratorState;
    private Date changedStateTimestamp;
    Actuator refrigeratorActuator;
    private BufferRegistration<Temperature> refrigeratorBufferRegistration;

    /**
     * Handle the start of the registration request.
     */
    @Override
    protected List<? extends ResourceMessage> startRegistration(RefrigeratorState refrigeratorState) {
        // safe current state of the refrigerator
        currentRefrigeratorState = refrigeratorState;
        changedStateTimestamp = context.currentTime();

        // ---- Buffer registration ----
        // create a refrigerator actuator for electricity
        refrigeratorActuator = new Actuator(REFRIGIRATOR_ACTUATOR_ID,
                                            "Miele Refrigerator",
                                            CommoditySet.onlyElectricity);

        // create a buffer registration message
        Set<Actuator> actuators = new HashSet<Actuator>();
        actuators.add(refrigeratorActuator);
        refrigeratorBufferRegistration = new BufferRegistration<Temperature>(config.resourceId(),
                                                                             changedStateTimestamp,
                                                                             Measure.zero(SECOND),
                                                                             "Temperature level",
                                                                             SI.CELSIUS,
                                                                             actuators);

        // ---- Buffer system description ----
        // create a behavior of the refrigerator
        ActuatorBehaviour refrigeratorActuatorBehaviour = makeRefrigeratorActuatorBehaviour(refrigeratorActuator.getActuatorId(),
                                                                                            refrigeratorState);

        // create the leakage function of the refrigerator
        FillLevelFunction<LeakageRate> bufferLeakageFunction = FillLevelFunction.<LeakageRate> create(config.minimumTemperature())
                                                                                .add(config.maximumTemperature(),
                                                                                     new LeakageRate(config.selfDischarge()))
                                                                                .build();

        // create the buffer system description message
        Set<ActuatorBehaviour> actuatorsBehaviours = new HashSet<ActuatorBehaviour>();
        actuatorsBehaviours.add(refrigeratorActuatorBehaviour);
        BufferSystemDescription sysDescr = new BufferSystemDescription(refrigeratorBufferRegistration,
                                                                       changedStateTimestamp,
                                                                       changedStateTimestamp,
                                                                       actuatorsBehaviours,
                                                                       bufferLeakageFunction);

        // ---- Buffer state update ----
        // create running mode
        Set<ActuatorUpdate> currentRunningMode = makeRefrigeratorRunningModes(refrigeratorActuator.getActuatorId(),
                                                                              refrigeratorState.getSupercoolMode());
        // create buffer state update message
        BufferStateUpdate<Temperature> update = new BufferStateUpdate<Temperature>(refrigeratorBufferRegistration,
                                                                                   changedStateTimestamp,
                                                                                   changedStateTimestamp,
                                                                                   refrigeratorState.getCurrentTemperature(),
                                                                                   currentRunningMode);

        logger.debug("refrigerator manager start registration completed.");
        // return the three messages
        return Arrays.asList(refrigeratorBufferRegistration, sysDescr, update);
    }

    /**
     * Handle the update state request.
     */
    @SuppressWarnings("unchecked")
    @Override
    protected List<? extends ResourceMessage> updatedState(RefrigeratorState refrigeratorState) {
        logger.debug("Receive refrigerator state update, supercooling= " + refrigeratorState.getSupercoolMode());

        // Do nothing if there is no useful refrigerator state change request
        if (refrigeratorState.equals(currentRefrigeratorState)) {
            return Collections.emptyList();
        } else { // refrigerator state change request
            // save previous state
            currentRefrigeratorState = refrigeratorState;
            changedStateTimestamp = context.currentTime();

            // this state change is immediately valid
            Date validFrom = changedStateTimestamp;

            // create running mode
            Set<ActuatorUpdate> currentRunningMode = makeRefrigeratorRunningModes(refrigeratorActuator.getActuatorId(),
                                                                                  refrigeratorState.getSupercoolMode());
            // create buffer state update message
            BufferStateUpdate<Temperature> update = new BufferStateUpdate<Temperature>(refrigeratorBufferRegistration,
                                                                                       changedStateTimestamp,
                                                                                       validFrom,
                                                                                       refrigeratorState.getCurrentTemperature(),
                                                                                       currentRunningMode);
            // return state update message
            return Arrays.asList(update);
        }
    }

    /**
     * Handle a received allocation from the energy app.
     */
    @Override
    protected RefrigeratorControlParameters receivedAllocation(ResourceMessage message) {
        // is the message a buffer allocation?
        if (message instanceof BufferAllocation) {
            BufferAllocation bufferAllocation = (BufferAllocation) message;
            logger.debug("Received allocation " + bufferAllocation);

            // process all actuator allocations
            Set<ActuatorAllocation> allocations = bufferAllocation.getActuatorAllocations();
            for (ActuatorAllocation allocation : allocations) {

                // Derive energy app's decision
                boolean supercool;
                if (allocation.getRunningModeId() == RUNNINGMODE_SUPERCOOL) {
                    supercool = true;
                } else {
                    supercool = false;
                }

                // determine refrigerator mode and specified delay.
                long delay = allocation.getStartTime().getTime() - context.currentTimeMillis();
                delay = (delay <= 0 ? 1 : delay);

                // set up the switch to the refrigerator mode after the specified delay
                scheduleSwitchToRefrigeratorMode(Measure.valueOf(delay, MS), supercool);

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
    private void scheduleSwitchToRefrigeratorMode(final Measurable<Duration> delay, final boolean supercooling) {
        final Runnable allocationHelper = new Runnable() {
            @Override
            public void run() {
                sendControlParameters(new RefrigeratorControlParameters() {

                    @Override
                    public boolean getSupercoolMode() {
                        return supercooling;
                    }
                });
                logger.debug("Has set up allocation at " + delay + " for supercool mode=" + supercooling);
            }
        };
        context.schedule(allocationHelper, delay);
    }

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        // create a configuration
        config = Configurable.createConfigurable(Config.class, properties);
        logger.debug("RefrigeratorManager Activated");
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
    private ActuatorBehaviour makeRefrigeratorActuatorBehaviour(int actuatorId, RefrigeratorState state) {
        // make three transitions holders
        Transition toChargingTransition = makeTransition(RUNNINGMODE_SUPERCOOL);
        Transition toIdleTransition = makeTransition(RUNNINGMODE_IDLE);

        // create the transition graph, it is fully connected in this case
        Set<Transition> idleTransition = new HashSet<Transition>();
        idleTransition.add(toChargingTransition);
        Set<Transition> chargeTransition = new HashSet<Transition>();
        chargeTransition.add(toIdleTransition);

        FillLevelFunction<RunningModeBehaviour> chargeFillLevelFunctions, idleFillLevelFunctions, dischargeFillLevelFunctions;

        chargeFillLevelFunctions = FillLevelFunction.<RunningModeBehaviour> create(config.minimumTemperature())
                                                    .add(config.maximumTemperature(),
                                                         new RunningModeBehaviour(-0.2,
                                                                                  CommodityMeasurables.electricity(Measure.valueOf(300,
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
        RunningMode<FillLevelFunction<RunningModeBehaviour>> chargeRunningMode = new RunningMode<FillLevelFunction<RunningModeBehaviour>>(RUNNINGMODE_SUPERCOOL,
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
     * Create the refrigerator actuator set, based on the refrigerator mode
     *
     * @param refrigeratorMode
     * @return
     */
    private Set<ActuatorUpdate> makeRefrigeratorRunningModes(int actuatorId, boolean superCoolMode) {
        Set<ActuatorUpdate> runningModes = new HashSet<ActuatorUpdate>();
        ActuatorUpdate actuatorUpdate;
        if (superCoolMode) {
            actuatorUpdate = new ActuatorUpdate(actuatorId, RUNNINGMODE_SUPERCOOL, null);
        }
        else {
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
