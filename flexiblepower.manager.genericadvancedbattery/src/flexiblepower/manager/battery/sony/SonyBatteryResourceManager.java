package flexiblepower.manager.battery.sony;

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
import flexiblepower.manager.genericadvancedbattery.GenericAdvancedBatteryConfig;
import flexiblepower.manager.genericadvancedbattery.GenericAdvancedBatteryDeviceModel;
import flexiblepower.manager.genericadvancedbattery.GenericAdvancedBatteryResourceManager;

@Component(designateFactory = SonyBatteryConfig.class, provide = Endpoint.class, immediate = true)
public class SonyBatteryResourceManager extends GenericAdvancedBatteryResourceManager {

    private SonyBatteryConfig sonyConfiguration;

    @Override
    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        try {
            sonyConfiguration = Configurable.createConfigurable(SonyBatteryConfig.class, properties);

            Map<String, Object> newProperties = new HashMap<String, Object>();
            newProperties.put("resourceId", sonyConfiguration.resourceId());
            newProperties.put("totalCapacityKWh", sonyConfiguration.nrOfmodules() * 1.2);
            newProperties.put("maximumChargingRateWatts", sonyConfiguration.nrOfmodules() == 1 ? 2500 : 5000);
            newProperties.put("maximumDischargingRateWatts", sonyConfiguration.nrOfmodules() == 1 ? 2500 : 5000);
            newProperties.put("nrOfCyclesBeforeEndOfLife", 6000);
            newProperties.put("initialSocRatio", sonyConfiguration.initialSocRatio());
            newProperties.put("nrOfModulationSteps", 19);
            newProperties.put("minimumFillLevelPercent", sonyConfiguration.minimumFillLevelPercent());
            newProperties.put("maximumFillLevelPercent", sonyConfiguration.maximumFillLevelPercent());
            newProperties.put("updateIntervalSeconds", sonyConfiguration.updateIntervalSeconds());

            // Advanced batteryModel settings
            newProperties.put("ratedVoltage", 52.6793);
            newProperties.put("KValue", 0.011);
            newProperties.put("QAmpereHours", 24);
            newProperties.put("constantA", 3);
            newProperties.put("constantB", 2.8);
            newProperties.put("internalResistanceOhms", 0.036);

            // Create a config
            config = Configurable.createConfigurable(GenericAdvancedBatteryConfig.class, newProperties);

            // Initialize the batteryModel correctly to start the first time step.
            batteryModel = new GenericAdvancedBatteryDeviceModel(config, context);

            scheduledFuture = context.scheduleAtFixedRate(this,
                                                          Measure.valueOf(0, SI.SECOND),
                                                          Measure.valueOf(config.updateIntervalSeconds(),
                                                                          SI.SECOND));

            widget = new SonyBatteryWidget(batteryModel);
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

    @Override
    @Reference(optional = false, dynamic = false, multiple = false)
    public void setContext(FlexiblePowerContext context) {
        this.context = context;
    }
}
