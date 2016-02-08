package flexiblepower.manager.genericadvancedbattery;

import static javax.measure.unit.SI.SECOND;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Money;
import javax.measure.quantity.Power;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

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
import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.MessageHandler;
import org.flexiblepower.ral.messages.AllocationRevoke;
import org.flexiblepower.ral.messages.ResourceMessage;
import org.flexiblepower.ral.values.Commodity;
import org.flexiblepower.ral.values.CommodityMeasurables;
import org.flexiblepower.ral.values.CommoditySet;
import org.flexiblepower.ui.Widget;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;

/**
 * This is a ResourceManager for the generic Battery batteryModel. This manager includes the batteryModel, so there is
 * no need to connect it to a driver.
 *
 * This is the generic batteryModel. There are specialized models which inherit from thil class.
 */
@Component(designateFactory = GenericAdvancedBatteryConfig.class, provide = Endpoint.class, immediate = true)
public class GenericAdvancedBatteryResourceManager implements BufferResourceManager, Runnable, MessageHandler {

    private static final int BATTERY_CHARGER_ACTUATOR_ID = 0;

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    /** Configuration of the component */
    protected GenericAdvancedBatteryConfig config;
    /** FlexiblePowerContext */
    protected FlexiblePowerContext context;
    /** The actual batteryModel */
    protected GenericAdvancedBatteryDeviceModel batteryModel;
    /** Widget for the battery batteryModel, can be different for subclasses */
    protected Widget widget;
    /** Reference to the Widget registration in the Service Registry */
    protected ServiceRegistration<Widget> widgetRegistration;
    /** Schedule this Runnable */
    protected ScheduledFuture<?> scheduledFuture;
    /** Object describing the charger */
    private Actuator batteryCharger;
    /** EFI buffer registration message */
    private BufferRegistration<Dimensionless> batteryBufferRegistration;
    /** Connection to the Controller (Energy App) */
    private Connection controllerConnection;
    /** Map with all the RunningModes, runningmode ID is used as key */
    private HashMap<Integer, RunningMode<FillLevelFunction<RunningModeBehaviour>>> runningModes;

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        try {
            // Create a config
            config = Configurable.createConfigurable(GenericAdvancedBatteryConfig.class, properties);

            // Initialize the batteryModel correctly to start the first time step.
            batteryModel = new GenericAdvancedBatteryDeviceModel(config, context);

            scheduledFuture = context.scheduleAtFixedRate(this,
                                                          Measure.valueOf(0, SI.SECOND),
                                                          Measure.valueOf(config.updateIntervalSeconds(),
                                                                          SI.SECOND));

            widget = new GenericAdvancedBatteryWidget(batteryModel);
            widgetRegistration = bundleContext.registerService(Widget.class, widget, null);
            logger.debug("Advanced Battery Manager activated");
        } catch (Exception ex) {
            logger.error("Error during initialization of the battery simulation: " + ex.getMessage(), ex);
            deactivate();
        }
    }

    @Deactivate
    public void deactivate() {
        logger.debug("Advanced Battery Manager deactivated");
        if (widgetRegistration != null) {
            widgetRegistration.unregister();
        }
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }

    @Reference(optional = false, dynamic = false, multiple = false)
    public void setContext(FlexiblePowerContext context) {
        this.context = context;
    }

    protected List<? extends ResourceMessage> startRegistration() {
        // safe current state of the battery
        Date now = context.currentTime();

        // ---- Buffer registration ----
        // create a battery actuator for electricity
        batteryCharger = new Actuator(BATTERY_CHARGER_ACTUATOR_ID, "Battery Charger 1", CommoditySet.onlyElectricity);
        // create a buffer registration message
        batteryBufferRegistration = new BufferRegistration<Dimensionless>(config.resourceId(),
                                                                          now,
                                                                          Measure.zero(SECOND),
                                                                          "Battery state of charge in percent",
                                                                          NonSI.PERCENT,
                                                                          Collections.<Actuator> singleton(batteryCharger));

        // ---- Buffer system description ----
        // create a behavior of the battery
        ActuatorBehaviour batteryActuatorBehaviour = createBatteryActuatorBehaviour(batteryCharger.getActuatorId());
        // create the leakage function of the battery
        FillLevelFunction<LeakageRate> bufferLeakageFunction = FillLevelFunction.<LeakageRate> create(0d)
                                                                                .add(100d, new LeakageRate(0))
                                                                                .build(); // TODO build in
                                                                                          // leakage???
        // create the buffer system description message
        Set<ActuatorBehaviour> actuatorsBehaviours = new HashSet<ActuatorBehaviour>();
        actuatorsBehaviours.add(batteryActuatorBehaviour);
        BufferSystemDescription sysDescr = new BufferSystemDescription(batteryBufferRegistration,
                                                                       now,
                                                                       now,
                                                                       actuatorsBehaviours,
                                                                       bufferLeakageFunction);

        // ---- Buffer state update ----
        BufferStateUpdate<Dimensionless> update = createBufferStateUpdate(now);

        logger.debug("Battery manager start registration completed.");
        // return the three messages
        return Arrays.asList(batteryBufferRegistration, sysDescr, update);
    }

    private BufferStateUpdate<Dimensionless> createBufferStateUpdate(Date timestamp) {
        // create running mode
        int currentRunningMode = findRunningModeWithPower(batteryModel.getElectricPower().doubleValue(SI.WATT));
        Set<ActuatorUpdate> actuatorUpdates = Collections
                                                         .<ActuatorUpdate> singleton(new ActuatorUpdate(BATTERY_CHARGER_ACTUATOR_ID,
                                                                                                        currentRunningMode,
                                                                                                        null));
        // Cap the currentFillLevel so there will always be a RunningMode defined for the currentFillLevel
        double currentFillLevel = batteryModel.getCurrentFillLevel().doubleValue(NonSI.PERCENT);
        currentFillLevel = Math.max(Math.min(currentFillLevel, config.maximumFillLevelPercent()),
                                    config.minimumFillLevelPercent());
        // create buffer state update message
        BufferStateUpdate<Dimensionless> update = new BufferStateUpdate<Dimensionless>(batteryBufferRegistration,
                                                                                       timestamp,
                                                                                       timestamp,
                                                                                       Measure.valueOf(currentFillLevel,
                                                                                                       NonSI.PERCENT),
                                                                                       actuatorUpdates);
        return update;
    }

    @Override
    public MessageHandler onConnect(Connection connection) {
        // Connect to the Controller
        controllerConnection = connection;
        for (ResourceMessage m : startRegistration()) {
            connection.sendMessage(m);
        }
        return this;
    }

    @Override
    public void handleMessage(Object message) {
        if (message instanceof BufferAllocation) {
            handleBufferAllocation((BufferAllocation) message);
        } else if (message instanceof AllocationRevoke) {
            handleAllocationRevoke((AllocationRevoke) message);
        } else {
            logger.warn("Received unknown message type: " + message.getClass().getSimpleName());
        }
    }

    private void handleAllocationRevoke(AllocationRevoke message) {
        // TODO Auto-generated method stub
    }

    private void handleBufferAllocation(BufferAllocation message) {
        for (ActuatorAllocation allocation : message.getActuatorAllocations()) {
            if (allocation.getActuatorId() == batteryCharger.getActuatorId()) {
                // This one is for us!
                if (runningModes.containsKey(allocation.getRunningModeId())) {
                    Measurable<Power> desiredChargePower = runningModes.get(allocation.getRunningModeId())
                                                                       .getValue()
                                                                       .getValueForFillLevel(batteryModel.getCurrentFillLevel()
                                                                                                         .doubleValue(NonSI.PERCENT))
                                                                       .getCommodityConsumption()
                                                                       .get(Commodity.ELECTRICITY);

                    // This method also updates the batteryModel
                    batteryModel.setDesiredChargePower(desiredChargePower);

                    // TODO use: aa.getStartTime();

                    // Send the state update
                    controllerConnection.sendMessage(createBufferStateUpdate(context.currentTime()));
                } else {
                    logger.warn("Received allocation for non-existing runningmode: " + allocation.getRunningModeId());
                }
            }
        }
    }

    @Override
    public void disconnected() {
        controllerConnection = null;
    }

    /**
     * Helper method to set up everything for the battery actuator behavior specification
     *
     * @param actuatorId
     * @return Returns the completely filled battery actuator behavior object
     */
    private ActuatorBehaviour createBatteryActuatorBehaviour(int actuatorId) {

        int nrOfRunningModes = 3 + 2 * config.nrOfModulationSteps();

        // Create a set of all the Transitions. Since every transition is
        // allowed from every RunningMode to every other RunningMode, we can
        // give every RunningMode the same set of all transitions.
        Set<Transition> transitions = new HashSet<Transition>();
        for (int runnningModeId = 0; runnningModeId < nrOfRunningModes; runnningModeId++) {
            transitions.add(makeTransition(runnningModeId));
        }

        // Create RunningModes
        int runningModeId = 0;
        runningModes = new HashMap<Integer, RunningMode<FillLevelFunction<RunningModeBehaviour>>>();
        // Charging RunningModes
        double increment = config.maximumChargingRateWatts() / (config.nrOfModulationSteps() + 1);
        for (int i = 0; i < config.nrOfModulationSteps() + 1; i++) {
            double power = config.maximumChargingRateWatts() - (increment * i);
            runningModes.put(runningModeId,
                             createRunningMode(runningModeId, transitions, Measure.valueOf(power, SI.WATT)));
            runningModeId++;
        }

        // Idle RunningMode
        runningModes.put(runningModeId, createRunningMode(runningModeId, transitions, Measure.zero(SI.WATT)));
        runningModeId++;

        // Discharging RunningModes
        increment = config.maximumDischargingRateWatts() / (config.nrOfModulationSteps() + 1);
        for (int i = config.nrOfModulationSteps(); i >= 0; i--) {
            double power = -config.maximumDischargingRateWatts() + (increment * i);
            runningModes.put(runningModeId,
                             createRunningMode(runningModeId, transitions, Measure.valueOf(power, SI.WATT)));
            runningModeId++;
        }

        // return the actuator behavior with the three running modes for the
        // specified actuator id
        return new ActuatorBehaviour(actuatorId, runningModes.values());
    }

    private RunningMode<FillLevelFunction<RunningModeBehaviour>> createRunningMode(int runningModeId,
                                                                                   Set<Transition> transitions,
                                                                                   Measurable<Power> power) {
        String name;
        if (power.doubleValue(SI.WATT) > 0) {
            name = "charging, " + power;
        } else if (power.doubleValue(SI.WATT) < 0) {
            name = "discharging, " + power;
        } else {
            name = "idle";
        }
        return new RunningMode<FillLevelFunction<RunningModeBehaviour>>(runningModeId,
                                                                        name,
                                                                        createFillLevelFunction(power),
                                                                        transitions);
    }

    private FillLevelFunction<RunningModeBehaviour> createFillLevelFunction(Measurable<Power> power) {
        // Here we check to enforce the charging limitations near the minimum and maximum for battery health reasons.
        double lowerBoundPercent = config.minimumFillLevelPercent();
        double upperBoundPercent = config.maximumFillLevelPercent();

        if (power.doubleValue(SI.WATT) > config.batterySavingPowerWatts()) {
            upperBoundPercent = Math.min(upperBoundPercent, 95);
        } else if (power.doubleValue(SI.WATT) < -config.batterySavingPowerWatts()) {
            lowerBoundPercent = Math.max(lowerBoundPercent, 5);
        }

        // Als chargen dan niet groter dan 500w en als dischargen dan niet groter dan -500W.
        return FillLevelFunction.<RunningModeBehaviour> create(lowerBoundPercent)
                                .add(upperBoundPercent,
                                     new RunningModeBehaviour(
                                                              power.doubleValue(SI.WATT)
                                                              / batteryModel.getTotalCapacity()
                                                                            .doubleValue(SI.JOULE)
                                                              * 100d
                                                              * batteryModel.getDischargeEfficiency(power),
                                                              CommodityMeasurables.electricity(power),
                                                              Measure.zero(NonSI.EUR_PER_HOUR)))
                                .build();
    }

    /**
     * Make a transition with no timers, no cost and the specified transition id
     *
     * @param toRunningMode
     * @return
     */
    private Transition makeTransition(int toRunningMode) {
        // no timers
        Set<Timer> startTimers = null;
        Set<Timer> blockingTimers = null;
        Measurable<Duration> transitionTime = Measure.zero(SECOND);

        // no cost
        Measurable<Money> transitionCosts = Measure.zero(NonSI.EUROCENT);

        // return transition
        return new Transition(toRunningMode, startTimers, blockingTimers, transitionCosts, transitionTime);
    }

    /**
     * Find the RunningMode that is closest to the given power value
     *
     * @param powerWatt
     *            The power in Watts to look for
     * @return the RunningMode that is closest to the given power value
     */
    private int findRunningModeWithPower(double powerWatt) {
        double fillLevel = batteryModel.getCurrentFillLevel().doubleValue(NonSI.PERCENT);
        double bestDistance = Double.POSITIVE_INFINITY;
        int bestRmId = 0;
        for (Entry<Integer, RunningMode<FillLevelFunction<RunningModeBehaviour>>> e : runningModes.entrySet()) {
            double rmPower = e.getValue()
                              .getValue()
                              .getValueForFillLevel(fillLevel)
                              .getCommodityConsumption()
                              .get(Commodity.ELECTRICITY)
                              .doubleValue(SI.WATT);
            double distance = Math.abs(powerWatt - rmPower);
            if (distance < bestDistance) {
                distance = bestDistance;
                bestRmId = e.getKey();
            }
        }
        return bestRmId;
    }

    /**
     * Update the model, and when connected send a BufferStateUpdate to the Controller.
     */
    @Override
    public void run() {
        // Update the batteryModel
        batteryModel.run();
        // Send the state update
        if (controllerConnection != null) {
            BufferStateUpdate<Dimensionless> bufferStateUpdate = createBufferStateUpdate(context.currentTime());
            controllerConnection.sendMessage(bufferStateUpdate);
        }
    }

}
