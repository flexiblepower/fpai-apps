package flexiblepower.manager.genericadvancedbattery;

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
import org.flexiblepower.ral.drivers.battery.BatteryMode;
import org.flexiblepower.ral.messages.AllocationRevoke;
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

@Component(designateFactory = GenericAdvancedBatteryConfig.class, provide = Endpoint.class, immediate = true)
public class GenericAdvancedBatteryResourceManager implements BufferResourceManager, Runnable, MessageHandler {

	protected final Logger logger = LoggerFactory.getLogger(this.getClass());

	private static final int BATTERY_CHARGER_ID = 0;

	protected GenericAdvancedBatteryConfig configuration;
	protected FlexiblePowerContext context;
	protected GenericAdvancedBatteryDeviceModel model;

	protected Widget widget;

	protected ServiceRegistration<Widget> widgetRegistration;

	protected ScheduledFuture<?> scheduledFuture;

	private Actuator batteryCharger;

	private BufferRegistration<Dimensionless> batteryBufferRegistration;

	private Connection controllerConnection;

	@Activate
	public void activate(BundleContext bundleContext, Map<String, Object> properties) {
		try {
			// Create a configuration
			configuration = Configurable.createConfigurable(GenericAdvancedBatteryConfig.class, properties);

			// Initialize the model correctly to start the first time step.
			model = new GenericAdvancedBatteryDeviceModel(configuration, context);

			scheduledFuture = this.context.scheduleAtFixedRate(this, Measure.valueOf(0, SI.SECOND),
					Measure.valueOf(configuration.updateIntervalSeconds(), SI.SECOND));

			widget = new GenericAdvancedBatteryWidget(this.model);
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
		// Because we're not connected to a driver we'll not use the state
		// object.

		logger.debug("Updated the battery state update, mode=" + model.getCurrentMode().name());

		// safe current state of the battery
		Date now = context.currentTime();

		// ---- Buffer registration ----
		// create a battery actuator for electricity
		batteryCharger = new Actuator(BATTERY_CHARGER_ID, "Battery Charger 1", CommoditySet.onlyElectricity);
		// create a buffer registration message
		batteryBufferRegistration = new BufferRegistration<Dimensionless>(configuration.resourceId(), now,
				Measure.zero(SECOND), "Battery state of charge in percent", NonSI.PERCENT,
				Collections.<Actuator> singleton(batteryCharger));

		// ---- Buffer system description ----
		// create a behavior of the battery
		ActuatorBehaviour batteryActuatorBehaviour = makeBatteryActuatorBehaviour(batteryCharger.getActuatorId());
		// create the leakage function of the battery
		FillLevelFunction<LeakageRate> bufferLeakageFunction = FillLevelFunction.<LeakageRate> create(0d)
				.add(100d, new LeakageRate(0)).build(); // TODO build in
														// leakage???
		// create the buffer system description message
		Set<ActuatorBehaviour> actuatorsBehaviours = new HashSet<ActuatorBehaviour>();
		actuatorsBehaviours.add(batteryActuatorBehaviour);
		BufferSystemDescription sysDescr = new BufferSystemDescription(batteryBufferRegistration, now, now,
				actuatorsBehaviours, bufferLeakageFunction);

		// ---- Buffer state update ----
		BufferStateUpdate<Dimensionless> update = createBufferStateUpdate(now);

		logger.debug("Battery manager start registration completed.");
		// return the three messages
		return Arrays.asList(batteryBufferRegistration, sysDescr, update);
	}

	private BufferStateUpdate<Dimensionless> createBufferStateUpdate(Date timestamp) {
		// create running mode
		Set<ActuatorUpdate> currentRunningMode = makeBatteryRunningModes(batteryCharger.getActuatorId(),
				model.getCurrentMode());
		// create buffer state update message
		Measurable<Dimensionless> currentFillLevel = model.getCurrentFillLevel();
		BufferStateUpdate<Dimensionless> update = new BufferStateUpdate<Dimensionless>(batteryBufferRegistration,
				timestamp, timestamp, currentFillLevel, currentRunningMode);
		return update;
	}

	private ControlSpaceRevoke createRevokeMessage() {
		return new ControlSpaceRevoke(configuration.resourceId(), context.currentTime());
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
		for (ActuatorAllocation allocation : message.getActuatorAllocations()) {
			if (allocation.getActuatorId() == batteryCharger.getActuatorId()) {
				// This one is for us!
				GenericAdvancedBatteryMode desiredRunningMode = GenericAdvancedBatteryMode
						.getByRunningModeId(allocation.getRunningModeId());
				model.goToRunningMode(desiredRunningMode); // also updates model
				// TODO use: aa.getStartTime();

				// Send the state update
				controllerConnection.sendMessage(createBufferStateUpdate(context.currentTime()));
			}
		}
	}

	@Override
	public void disconnected() {
		this.controllerConnection = null;
	}

	/**
	 * Helper method to set up everything for the battery actuator behavior
	 * specification
	 *
	 * @param actuatorId
	 * @return Returns the completely filled battery actuator behavior object
	 */
	private ActuatorBehaviour makeBatteryActuatorBehaviour(int actuatorId) {
		// make three transitions holders
		Transition toChargingTransition = makeTransition(GenericAdvancedBatteryMode.CHARGE.runningModeId);
		Transition toIdleTransition = makeTransition(GenericAdvancedBatteryMode.IDLE.runningModeId);
		Transition toDisChargingTransition = makeTransition(GenericAdvancedBatteryMode.DISCHARGE.runningModeId);

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

		FillLevelFunction<RunningModeBehaviour> chargeFillLevelFunction, idleFillLevelFunction,
				dischargeFillLevelFunction;

		Measurable<Power> chargeSpeed = Measure.valueOf(configuration.maximumChargingRateWatts(), SI.WATT);

		chargeFillLevelFunction = FillLevelFunction
				.<RunningModeBehaviour> create(configuration.minimumFillLevelPercent())
				.add(configuration.maximumFillLevelPercent(),
						new RunningModeBehaviour(
								chargeSpeed.doubleValue(WATT) / model.getTotalCapacity().doubleValue(SI.JOULE) * 100d
										* model.getChargeEfficiency(chargeSpeed),
								CommodityMeasurables.electricity(chargeSpeed), Measure.zero(NonSI.EUR_PER_HOUR)))
				.build();

		idleFillLevelFunction = FillLevelFunction.<RunningModeBehaviour> create(configuration.minimumFillLevelPercent())
				.add(configuration.maximumFillLevelPercent(), new RunningModeBehaviour(0,
						CommodityMeasurables.electricity(Measure.zero(WATT)), Measure.zero(NonSI.EUR_PER_HOUR)))
				.build();

		Measurable<Power> dischargeSpeed = Measure.valueOf(-1 * configuration.maximumDischargingRateWatts(), SI.WATT);

		dischargeFillLevelFunction = FillLevelFunction
				.<RunningModeBehaviour> create(configuration.minimumFillLevelPercent())
				.add(configuration.maximumFillLevelPercent(),
						new RunningModeBehaviour(
								dischargeSpeed.doubleValue(WATT) / model.getTotalCapacity().doubleValue(SI.JOULE) * 100d
										* model.getDischargeEfficiency(dischargeSpeed),
								CommodityMeasurables.electricity(dischargeSpeed), Measure.zero(NonSI.EUR_PER_HOUR)))
				.build();

		// Based on the fill level functions and the transitions, create the
		// three running modes
		RunningMode<FillLevelFunction<RunningModeBehaviour>> chargeRunningMode = new RunningMode<FillLevelFunction<RunningModeBehaviour>>(
				GenericAdvancedBatteryMode.CHARGE.runningModeId, "charging", chargeFillLevelFunction, chargeTransition);
		RunningMode<FillLevelFunction<RunningModeBehaviour>> idleRunningMode = new RunningMode<FillLevelFunction<RunningModeBehaviour>>(
				GenericAdvancedBatteryMode.IDLE.runningModeId, "idle", idleFillLevelFunction, idleTransition);
		RunningMode<FillLevelFunction<RunningModeBehaviour>> dischargeRunningMode = new RunningMode<FillLevelFunction<RunningModeBehaviour>>(
				GenericAdvancedBatteryMode.DISCHARGE.runningModeId, "discharging", dischargeFillLevelFunction,
				dischargeTransition);

		// return the actuator behavior with the three running modes for the
		// specified actuator id
		return ActuatorBehaviour.create(actuatorId).add(idleRunningMode).add(chargeRunningMode)
				.add(dischargeRunningMode).build();
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
		return new Transition(transitionId, startTimers, blockingTimers, transitionCosts, transitionTime);
	}

	/**
	 * Create the battery actuator set, based on the battery mode
	 *
	 * @param advancedBatteryMode
	 * @return
	 */
	private Set<ActuatorUpdate> makeBatteryRunningModes(int actuatorId, GenericAdvancedBatteryMode advancedBatteryMode) {
		return Collections
				.<ActuatorUpdate> singleton(new ActuatorUpdate(actuatorId, advancedBatteryMode.runningModeId, null));
	}

	@Override
	public void run() {
		// Update the model
		model.run();
		// Send the state update
		if (controllerConnection != null) {
			BufferStateUpdate<Dimensionless> bufferStateUpdate = createBufferStateUpdate(context.currentTime());
			controllerConnection.sendMessage(bufferStateUpdate);
		}
	}

}
