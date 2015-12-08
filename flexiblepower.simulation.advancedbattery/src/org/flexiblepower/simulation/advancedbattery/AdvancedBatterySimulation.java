package org.flexiblepower.simulation.advancedbattery;

import static javax.measure.unit.NonSI.KWH;
import static javax.measure.unit.SI.WATT;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.ral.drivers.battery.BatteryControlParameters;
import org.flexiblepower.ral.drivers.battery.BatteryDriver;
import org.flexiblepower.ral.drivers.battery.BatteryMode;
import org.flexiblepower.ral.drivers.battery.BatteryState;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.ui.Widget;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Modified;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import flexiblepower.manager.advancedbattery.AdvancedBatteryConfig;
import flexiblepower.manager.advancedbattery.AdvancedBatteryWidget;

@Component(designateFactory = AdvancedBatteryConfig.class, provide = Endpoint.class, immediate = true)
public class AdvancedBatterySimulation extends AbstractResourceDriver<BatteryState, BatteryControlParameters>
		implements BatteryDriver, Runnable {

	public Measurable<Energy> totalCapacityInKWh;
	public Measurable<Power> chargeSpeedInWatt;
	public Measurable<Power> dischargeSpeedInWatt;
	public Measurable<Power> selfDischargeSpeedInWatt;
	public AdvancedBatteryConfig configuration;
	public Measurable<Duration> minTimeOn;
	public Measurable<Duration> minTimeOff;

	private static final Logger logger = LoggerFactory.getLogger(AdvancedBatterySimulation.class);

	class State implements BatteryState {
		private final double stateOfCharge; // State of Charge is always within
											// [0, 1] range.
		private final BatteryMode mode;

		public State(double stateOfCharge, BatteryMode mode) {
			// This is a quick fix. It would be better to throw an exception.
			// This should be done later.
			if (stateOfCharge < 0.0) {
				stateOfCharge = 0.0;
			} else if (stateOfCharge > 1.0) {
				stateOfCharge = 1.0;
			}

			this.stateOfCharge = stateOfCharge;
			this.mode = mode;
		}

		@Override
		public boolean isConnected() {
			return true;
		}

		@Override
		public Measurable<Energy> getTotalCapacity() {
			return totalCapacityInKWh;
		}

		@Override
		public Measurable<Power> getChargeSpeed() {
			return chargeSpeedInWatt;
		}

		@Override
		public Measurable<Power> getDischargeSpeed() {
			return dischargeSpeedInWatt;
		}

		@Override
		public Measurable<Power> getSelfDischargeSpeed() {
			return selfDischargeSpeedInWatt;
		}

		@Override
		public double getChargeEfficiency() {
			return configuration.chargeEfficiency();
		}

		@Override
		public double getDischargeEfficiency() {
			return configuration.dischargeEfficiency();
		}

		@Override
		public Measurable<Duration> getMinimumOnTime() {
			return minTimeOn;
		}

		@Override
		public Measurable<Duration> getMinimumOffTime() {
			return minTimeOff;
		}

		@Override
		public double getStateOfCharge() {
			return stateOfCharge;
		}

		@Override
		public BatteryMode getCurrentMode() {
			return mode;
		}

		@Override
		public String toString() {
			return "State [stateOfCharge=" + stateOfCharge + ", mode=" + mode + "]";
		}
	}

	private FlexiblePowerContext context;
	private Date lastUpdatedTime;
	private ServiceRegistration<Widget> widgetRegistration;
	private ScheduledFuture<?> scheduledFuture;
	private AdvancedBatteryWidget widget;
	private State state;
	private double stateOfCharge;
	private BatteryMode mode;

	@Reference
	public void setContext(FlexiblePowerContext context) {
		this.context = context;
		lastUpdatedTime = context.currentTime();
	}

	@Activate
	public void activate(BundleContext context, Map<String, Object> properties) throws Exception {
		try {
			configuration = Configurable.createConfigurable(AdvancedBatteryConfig.class, properties);
			totalCapacityInKWh = Measure.valueOf(configuration.totalCapacity(), KWH);
			chargeSpeedInWatt = Measure.valueOf(configuration.chargePower(), WATT);
			dischargeSpeedInWatt = Measure.valueOf(configuration.dischargePower(), WATT);
			selfDischargeSpeedInWatt = Measure.valueOf(configuration.selfDischargePower(), WATT);
			stateOfCharge = configuration.initialStateOfCharge();
			minTimeOn = Measure.valueOf(0, SI.SECOND);
			minTimeOff = Measure.valueOf(0, SI.SECOND);
			mode = BatteryMode.IDLE;

			publishState(new State(stateOfCharge, mode));

			scheduledFuture = this.context.scheduleAtFixedRate(this, Measure.valueOf(0, SI.SECOND),
					Measure.valueOf(configuration.updateInterval(), SI.SECOND));

			widget = new AdvancedBatteryWidget(this);
			widgetRegistration = context.registerService(Widget.class, widget, null);
		} catch (Exception ex) {
			logger.error("Error during initialization of the battery simulation: " + ex.getMessage(), ex);
			deactivate();
			throw ex;
		}
	}

	@Modified
	public void modify(BundleContext context, Map<String, Object> properties) {
		try {
			configuration = Configurable.createConfigurable(AdvancedBatteryConfig.class, properties);

			totalCapacityInKWh = Measure.valueOf(configuration.totalCapacity(), KWH);
			chargeSpeedInWatt = Measure.valueOf(configuration.chargePower(), WATT);
			dischargeSpeedInWatt = Measure.valueOf(configuration.dischargePower(), WATT);
			selfDischargeSpeedInWatt = Measure.valueOf(configuration.selfDischargePower(), WATT);
			stateOfCharge = configuration.initialStateOfCharge();
			// TODO WHY 2 seconds??
			minTimeOn = Measure.valueOf(2, SI.SECOND);
			minTimeOff = Measure.valueOf(2, SI.SECOND);
			mode = BatteryMode.IDLE;
		} catch (RuntimeException ex) {
			logger.error("Error during initialization of the battery simulation: " + ex.getMessage(), ex);
			deactivate();
			throw ex;
		}
	}

	@Deactivate
	public void deactivate() {
		if (widgetRegistration != null) {
			widgetRegistration.unregister();
			widgetRegistration = null;
		}
		if (scheduledFuture != null) {
			scheduledFuture.cancel(false);
			scheduledFuture = null;
		}
	}

	@Override
	public void run() {
		Date currentTime = context.currentTime();
		double durationSinceLastUpdateInSeconds = (currentTime.getTime() - lastUpdatedTime.getTime()) / 1000.0;
		lastUpdatedTime = currentTime;
		logger.debug("Battery simulation step. Mode={} Timestep={}s", mode, durationSinceLastUpdateInSeconds);
		if (durationSinceLastUpdateInSeconds > 0) {
			double newStateOfCharge = 0;
			// TODO Add new SoC calculation.
			State state = new State(newStateOfCharge, mode);
			logger.debug("Publishing state {}", state);
			publishState(state);

			stateOfCharge = newStateOfCharge;
		} else {
			logger.warn("Duration since last update is 0.");
		}
	}

	@Override
	protected void handleControlParameters(BatteryControlParameters controlParameters) {
		mode = controlParameters.getMode();
	}

	public State getCurrentState() {
		return new State(stateOfCharge, mode);
	}
}