package flexiblepower.manager.battery.powerwall;

import java.util.HashMap;
import java.util.Map;

import javax.measure.Measure;
import javax.measure.unit.SI;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.ui.Widget;
import org.osgi.framework.BundleContext;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;
import flexiblepower.manager.genericadvancedbattery.GenericAdvancedBatteryConfig;
import flexiblepower.manager.genericadvancedbattery.GenericAdvancedBatteryDeviceModel;
import flexiblepower.manager.genericadvancedbattery.GenericAdvancedBatteryResourceManager;

@Component(designateFactory = PowerwallBatteryConfig.class, provide = Endpoint.class, immediate = true)
public class PowerwallBatteryResourceManager extends GenericAdvancedBatteryResourceManager {

	//TODO The real powerwall is less efficient (including inverter around 87% than the generic model). 
	private static final double CAPACITY_KWH = 7;
	private PowerwallBatteryConfig powerwallConfiguration;

	@Override
	@Activate
	public void activate(BundleContext bundleContext, Map<String, Object> properties) {
		try {
			powerwallConfiguration = Configurable.createConfigurable(PowerwallBatteryConfig.class, properties);

			Map<String, Object> newProperties = new HashMap<String,Object>();
			newProperties.put("resourceId", powerwallConfiguration.resourceId());
			newProperties.put("totalCapacityKWh", CAPACITY_KWH);
			newProperties.put("maximumChargingRateWatts", 2000);
			newProperties.put("maximumDischargingRateWatts", 2000);
			newProperties.put("numberOfCyclesBeforeEndOfLife", 4000);
			newProperties.put("initialSocRatio", powerwallConfiguration.initialSocRatio());
			newProperties.put("minimumFillLevelPercent", powerwallConfiguration.minimumFillLevelPercent());
			newProperties.put("maximumFillLevelPercent", powerwallConfiguration.maximumFillLevelPercent());
			newProperties.put("updateIntervalSeconds", powerwallConfiguration.updateIntervalSeconds());
			
			newProperties.put("ratedVoltage", 433.3507);
			newProperties.put("KValue", 0.12903);
			newProperties.put("QAmpereHours", 17.5);
			newProperties.put("constantA", 60);
			newProperties.put("constantB", 3.4893);
			newProperties.put("internalResistanceOhms", 0.22857);
			
			// Create a configuration
			configuration = Configurable.createConfigurable(GenericAdvancedBatteryConfig.class, newProperties);

			// Initialize the model correctly to start the first time step.
			model = new GenericAdvancedBatteryDeviceModel(configuration, context);

			scheduledFuture = this.context.scheduleAtFixedRate(this, Measure.valueOf(0, SI.SECOND),
					Measure.valueOf(configuration.updateIntervalSeconds(), SI.SECOND));

			widget = new PowerwallBatteryWidget(this.model);
			widgetRegistration = bundleContext.registerService(Widget.class, widget, null);
			logger.debug("Advanced Battery Manager activated");
		} catch (Exception ex) {
			logger.error("Error during initialization of the battery simulation: " + ex.getMessage(), ex);
			deactivate();
		}
	}

	@Override
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
}
