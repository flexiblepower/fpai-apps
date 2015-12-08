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

import javax.measure.Measure;
import javax.measure.quantity.Energy;
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
import org.flexiblepower.efi.util.FillLevelFunction;
import org.flexiblepower.messaging.Cardinality;
import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.MessageHandler;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.messaging.Ports;
import org.flexiblepower.ral.drivers.battery.BatteryControlParameters;
import org.flexiblepower.ral.drivers.battery.BatteryState;
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.ral.messages.AllocationRevoke;
import org.flexiblepower.ral.messages.AllocationStatusUpdate;
import org.flexiblepower.ral.messages.ControlSpaceRevoke;
import org.flexiblepower.ral.messages.ResourceMessage;
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
public class AdvancedBatteryResourceManager implements BufferResourceManager, Runnable, Endpoint, MessageHandler {

	private static final Logger logger = LoggerFactory.getLogger(AdvancedBatteryResourceManager.class);

	private static final int BATTERY_CHARGER_ID = 0;

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

	private BufferRegistration<Energy> batteryBufferRegistration;

	private Connection controllerConnection;

	@Activate
	public void activate(BundleContext bundleContext, Map<String, Object> properties) {
		try {
			// Create a configuration
			configuration = Configurable.createConfigurable(AdvancedBatteryConfig.class, properties);

			// Initialize the model correctly to start the first time step.
			model = new AdvancedBatteryDeviceModel(configuration);

			scheduledFuture = this.context.scheduleAtFixedRate(this, Measure.valueOf(0, SI.SECOND),
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
		batteryBufferRegistration = new BufferRegistration<Energy>(configuration.resourceId(), changedStateTimestamp,
				Measure.zero(SECOND), "Battery state of charge in kWH", NonSI.KWH,
				Collections.<Actuator> singleton(batteryCharger));

		// ---- Buffer system description ----
		// create a behavior of the battery
		ActuatorBehaviour batteryActuatorBehaviour = makeBatteryActuatorBehaviour(batteryCharger.getActuatorId(),
				batteryState);
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

	@Reference
	public void setContext(FlexiblePowerContext context) {
		lastUpdatedTime = context.currentTime();
		this.context = context;
		// Instantiate the model.

	}

	@Override
	public void run() {

	}

	@Override
	public MessageHandler onConnect(Connection connection) {
		// Connect to the Controller
		this.controllerConnection = connection;
		for(ResourceMessage m : this.startRegistration()) {
			connection.sendMessage(m);
		}
		return this;
	}

	@Override
	public void handleMessage(Object message) {
		if(message instanceof BufferAllocation) {
			handleBufferAllocation((BufferAllocation) message);
		} else if(message instanceof AllocationRevoke) {
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

}
