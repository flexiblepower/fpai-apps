package org.flexiblepower.zenobox.manager;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.efi.UncontrolledResourceManager;
import org.flexiblepower.efi.uncontrolled.UncontrolledMeasurement;
import org.flexiblepower.efi.uncontrolled.UncontrolledRegistration;
import org.flexiblepower.efi.uncontrolled.UncontrolledUpdate;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.ral.ResourceControlParameters;
import org.flexiblepower.ral.drivers.uncontrolled.PowerState;
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.ral.messages.ControlSpaceRevoke;
import org.flexiblepower.ral.messages.ResourceMessage;
import org.flexiblepower.ral.values.CommodityMeasurables;
import org.flexiblepower.ral.values.CommoditySet;
import org.flexiblepower.ral.values.ConstraintListMap;
import org.flexiblepower.ui.Widget;
import org.flexiblepower.zenobox.manager.ZenoBoxManager.Config;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = Endpoint.class, immediate = true)
@Port(name = "driver", accepts = PowerState.class)
public class ZenoBoxManager extends
                           AbstractResourceManager<PowerState, ResourceControlParameters> implements
                                                                                         UncontrolledResourceManager {

    private static final Logger logger = LoggerFactory.getLogger(ZenoBoxManager.class);

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "uncontrolled zenobox", description = "Resource identifier")
        String resourceId();

        @Meta.AD(deflt = "20", description = "Expiration of the ControlSpaces [s]", required = false)
        int expirationTime();

        @Meta.AD(deflt = "false", description = "Show simple widget")
        boolean showWidget();
    }

    private Config config;
    private FlexiblePowerContext context;
    private ZenoBoxManagerWidget widget;
    private ServiceRegistration<Widget> widgetRegistration;
    private Measurable<Power> lastDemand;
    private Date changedState;
    private Measure<Integer, Duration> allocationDelay;

    @Reference
    public void setContext(FlexiblePowerContext context) {
        this.context = context;
    }

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        config = Configurable.createConfigurable(Config.class, properties);

        // Configure widget if requested
        if (config.showWidget()) {
            widget = new ZenoBoxManagerWidget(this);
            widgetRegistration = bundleContext.registerService(Widget.class, widget, null);
        }

        logger.debug("Activated");
    };

    @Deactivate
    public void deactivate() {
        if (widgetRegistration != null) {
            widgetRegistration.unregister();
            widgetRegistration = null;
        }

        logger.debug("Deactivated");
    }

    public Measurable<Power> getLastDemand() {
        return lastDemand;
    }

    public String getResourceId() {
        return config.resourceId();
    }

    /**
     * Registration of THIS manager.
     */
    @Override
    protected List<? extends ResourceMessage> startRegistration(PowerState state) {
        changedState = context.currentTime();
        allocationDelay = Measure.valueOf(5, SI.SECOND);
        ConstraintListMap constraintList = ConstraintListMap.electricity(null); // this version of the uncontrolled
                                                                                // manager does not support
                                                                                // curtailments...
        UncontrolledRegistration reg = new UncontrolledRegistration(getResourceId(),
                                                                    changedState,
                                                                    allocationDelay,
                                                                    CommoditySet.onlyElectricity, constraintList);
        UncontrolledUpdate update = createUncontrolledUpdate(state);
        return Arrays.asList(reg, update);
    }

    /**
     * ZenoBox manager update that will be called periodically
     * 
     * @param state
     *            Power state that will be processed.
     * @return Returns an update...
     */
    private UncontrolledUpdate createUncontrolledUpdate(PowerState state) {

        // Get Current usage and update last demand
        Measurable<Power> currentUsage = state.getCurrentUsage();
        lastDemand = currentUsage;

        // Compose measurables updates
        CommodityMeasurables measurables = CommodityMeasurables.electricity(currentUsage);
        UncontrolledUpdate update = new UncontrolledMeasurement(getResourceId(),
                                                                changedState,
                                                                context.currentTime(),
                                                                measurables);

        logger.info("ZenoBox Manager update: " + currentUsage.toString());

        return update;
    }

    /**
     * This is a function called by AbstractResourceManager that will create update...
     */
    @Override
    protected List<? extends ResourceMessage> updatedState(PowerState state) {
        return Arrays.asList(createUncontrolledUpdate(state));
    }

    @Override
    protected ResourceControlParameters receivedAllocation(ResourceMessage message) {
        throw new AssertionError();
    }

    @Override
    protected ControlSpaceRevoke createRevokeMessage() {
        return new ControlSpaceRevoke(config.resourceId(), context.currentTime());
    }
}
