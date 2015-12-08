package flexiblepower.manager.advancedbattery;

import static javax.measure.unit.SI.SECOND;
import static javax.measure.unit.SI.WATT;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Money;
import javax.measure.quantity.Power;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.efi.BufferResourceManager;
import org.flexiblepower.efi.buffer.Actuator;
import org.flexiblepower.efi.buffer.ActuatorBehaviour;
import org.flexiblepower.efi.buffer.ActuatorUpdate;
import org.flexiblepower.efi.buffer.BufferAllocation;
import org.flexiblepower.efi.buffer.BufferRegistration;
import org.flexiblepower.efi.buffer.BufferStateUpdate;
import org.flexiblepower.efi.buffer.BufferSystemDescription;
import org.flexiblepower.efi.buffer.BufferUpdate;
import org.flexiblepower.efi.buffer.LeakageRate;
import org.flexiblepower.efi.buffer.RunningModeBehaviour;
import org.flexiblepower.efi.util.FillLevelFunction;
import org.flexiblepower.efi.util.RunningMode;
import org.flexiblepower.efi.util.Timer;
import org.flexiblepower.efi.util.Transition;
import org.flexiblepower.messaging.Cardinality;
import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.MessageHandler;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.ral.drivers.battery.BatteryMode;
import org.flexiblepower.ral.drivers.battery.BatteryState;
import org.flexiblepower.ral.messages.AllocationRevoke;
import org.flexiblepower.ral.messages.AllocationStatusUpdate;
import org.flexiblepower.ral.messages.ControlSpaceRevoke;
import org.flexiblepower.ral.messages.ResourceMessage;
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

@Component(designateFactory = AdvancedBatteryConfig.class, provide = Endpoint.class, immediate = true)
@Port(name = "controller", accepts = { BufferAllocation.class, AllocationRevoke.class }, sends = {
		BufferRegistration.class, BufferUpdate.class, AllocationStatusUpdate.class,
		ControlSpaceRevoke.class }, cardinality = Cardinality.SINGLE)
public class AdvancedBatteryResourceManager implements BufferResourceManager, Endpoint, MessageHandler {

	private static final Logger logger = LoggerFactory.getLogger(AdvancedBatteryResourceManager.class);

	private static final int BATTERY_CHARGER_ID = 0;

	private static final double MINIMUM_BUFFER_LEVEL = 0;

	private AdvancedBatteryConfig configuration;
	private FlexiblePowerContext context;
	private AdvancedBatteryDeviceModel model;

	private Date lastUpdatedTime;
	private Date changedStateTimestamp;

	private AdvancedBatteryWidget widget;

	private ServiceRegistration<Widget> widgetRegistration;

	private ScheduledFuture<?> scheduledFuture;

	private AdvancedBatteryDeviceModel previousModel;

	private Actuator batteryCharger;

	private BufferRegistration<Dimensionless> batteryBufferRegistration;

	private Connection controllerConnection;

	@Activate
	public void activate(BundleContext bundleContext, Map<String, Object> properties) {
		try {
			// Create a configuration
			configuration = Configurable.createConfigurable(AdvancedBatteryConfig.class, properties);

			// Initialize the model correctly to start the first time step.
			model = new AdvancedBatteryDeviceModel(configuration);

			scheduledFuture = this.context.scheduleAtFixedRate(model, Measure.valueOf(0, SI.SECOND),
					Measure.valueOf(configuration.updateIntervalSeconds(), SI.SECOND));

			widget = new AdvancedBatteryWidget(this.model);
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
		if(scheduledFuture != null) {
			scheduledFuture.cancel(true);
		}
	}

	protected List<? extends ResourceMessage> startRegistration() {
		// Because we're not connected to a driver we'll not use the state
		// object.

		logger.debug("Updated the battery state update, mode=" + model.getCurrentMode().name());

		// safe current state of the battery
		previousModel = model;
		changedStateTimestamp = context.currentTime();

		// ---- Buffer registration ----
		// create a battery actuator for electricity
		batteryCharger = new Actuator(BATTERY_CHARGER_ID, "Battery Charger 1", CommoditySet.onlyElectricity);
		// create a buffer registration message
		batteryBufferRegistration = new BufferRegistration<Dimensionless>(configuration.resourceId(), changedStateTimestamp,
				Measure.zero(SECOND), "Battery state of charge in percent", NonSI.PERCENT,
				Collections.<Actuator> singleton(batteryCharger));

		// ---- Buffer system description ----
		// create a behavior of the battery
		ActuatorBehaviour batteryActuatorBehaviour = makeBatteryActuatorBehaviour(batteryCharger.getActuatorId());
		// create the leakage function of the battery
		FillLevelFunction<LeakageRate> bufferLeakageFunction = FillLevelFunction
				.<LeakageRate> create(MINIMUM_BUFFER_LEVEL).add(batteryState.getTotalCapacity().doubleValue(UNIT_JOULE),
						new LeakageRate(batteryState.getSelfDischargeSpeed().doubleValue(WATT)))
				.build();
		// create the buffer system description message
		Set<ActuatorBehaviour> actuatorsBehaviours = new HashSet<ActuatorBehaviour>();
		actuatorsBehaviours.add(batteryActuatorBehaviour);
		BufferSystemDescription sysDescr = new BufferSystemDescription(batteryBufferRegistration, changedStateTimestamp,
				changedStateTimestamp, actuatorsBehaviours, bufferLeakageFunction);

		// ---- Buffer state update ----
		// create running mode
		Set<ActuatorUpdate> currentRunningMode = makeBatteryRunningModes(batteryCharger.getActuatorId(),
				batteryState.getCurrentMode());
		// create buffer state update message
		Measure<Double, Energy> currentFillLevel = getCurrentFillLevel(batteryState);
		BufferStateUpdate<Energy> update = new BufferStateUpdate<Energy>(batteryBufferRegistration,
				changedStateTimestamp, changedStateTimestamp, currentFillLevel, currentRunningMode);

		logger.debug("Battery manager start registration completed.");
		// return the three messages
		return Arrays.asList(batteryBufferRegistration, sysDescr, update);
	}

	private ControlSpaceRevoke createRevokeMessage() {
		return new ControlSpaceRevoke(configuration.resourceId(), context.currentTime());
	}

	@Reference(optional = false, dynamic = false, multiple = false)
	public void setContext(FlexiblePowerContext context) {
		lastUpdatedTime = context.currentTime();
		this.context = context;
		// Instantiate the model.

	}

	@Override
	public MessageHandler onConnect(Connection connection) {
		// Connect to the Controller
		this.controllerConnection = connection;
		for (ResourceMessage m : this.startRegistration()) {
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
		// TODO Auto-generated method stub

	}

	@Override
	public void disconnected() {
		this.controllerConnection = null;
	}
	
    /**
     * Helper method to set up everything for the battery actuator behavior specification
     *
     * @param actuatorId
     * @return Returns the completely filled battery actuator behavior object
     */
    private ActuatorBehaviour makeBatteryActuatorBehaviour(int actuatorId) {
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

        double totalCapacity = model.getTotalCapacity().doubleValue(NonSI.KWH);
        Measurable<Power> chargeSpeed = Measure.valueOf(1500, SI.WATT);

        chargeFillLevelFunctions = FillLevelFunction.<RunningModeBehaviour> create(configuration.minimumFillLevelPercent())
                                                    .add(configuration.maximumFillLevelPercent(),
                                                         new RunningModeBehaviour(chargeSpeed.doubleValue(WATT)
                                                                                  * model.getChargeEfficiency(chargeSpeed), // TODO translate to % per second
                                                                                  CommodityMeasurables.electricity(chargeSpeed),
                                                                                  Measure.zero(NonSI.EUR_PER_HOUR)))
                                                    .build();

        idleFillLevelFunctions = FillLevelFunction.<RunningModeBehaviour> create(configuration.minimumFillLevelPercent())
                                                  .add(configuration.maximumFillLevelPercent(),
                                                       new RunningModeBehaviour(0,
                                                                                CommodityMeasurables.electricity(Measure.zero(WATT)),
                                                                                Measure.zero(NonSI.EUR_PER_HOUR)))
                                                  .build();

        Measurable<Power> dischargeSpeed = Measure.valueOf(-1500, SI.WATT);
        // TODO: Check if discharge speed is the fill level change and the discharge/efficiency is correct...
        dischargeFillLevelFunctions = FillLevelFunction.<RunningModeBehaviour> create(configuration.minimumFillLevelPercent())
                                                       .add(configuration.maximumFillLevelPercent(),
                                                            new RunningModeBehaviour(dischargeSpeed.doubleValue(WATT) * model.getDischargeEfficiency(dischargeSpeed), // TODO translate to % per second
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

}
