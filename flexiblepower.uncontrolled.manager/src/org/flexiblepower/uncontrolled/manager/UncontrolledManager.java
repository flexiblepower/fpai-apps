package org.flexiblepower.uncontrolled.manager;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.efi.UncontrolledResourceManager;
import org.flexiblepower.efi.uncontrolled.UncontrolledMeasurement;
import org.flexiblepower.efi.uncontrolled.UncontrolledRegistration;
import org.flexiblepower.efi.uncontrolled.UncontrolledUpdate;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.rai.ResourceMessage;
import org.flexiblepower.rai.values.CommodityMeasurables;
import org.flexiblepower.rai.values.CommoditySet;
import org.flexiblepower.rai.values.ConstraintListMap;
import org.flexiblepower.ral.ResourceControlParameters;
import org.flexiblepower.ral.drivers.uncontrolled.PowerState;
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.time.TimeService;
import org.flexiblepower.ui.Widget;
import org.flexiblepower.uncontrolled.manager.UncontrolledManager.Config;
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
public class UncontrolledManager extends
                                AbstractResourceManager<PowerState, ResourceControlParameters> implements
                                                                                              UncontrolledResourceManager {

    private static final Logger log = LoggerFactory.getLogger(UncontrolledManager.class);

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "uncontrolled", description = "Resource identifier")
        String resourceId();

        @Meta.AD(deflt = "20", description = "Expiration of the ControlSpaces [s]", required = false)
        int expirationTime();

        @Meta.AD(deflt = "false", description = "Show simple widget")
        boolean showWidget();
    }

    private Config config;

    public UncontrolledManager() {
        super();
        log.debug("Initialized!");
    }

    private TimeService timeService;
    private UncontrolledManagerWidget widget;
    private ServiceRegistration<Widget> widgetRegistration;
    private Measurable<Power> lastDemand;
    private PowerState currentState;
    private Date changedState;
    private Measure<Integer, Duration> allocationDelay;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        config = Configurable.createConfigurable(Config.class, properties);
        if (config.showWidget()) {
            widget = new UncontrolledManagerWidget(this);
            widgetRegistration = bundleContext.registerService(Widget.class, widget, null);
        }
    };

    @Deactivate
    public void deactivate() {
        if (widgetRegistration != null) {
            widgetRegistration.unregister();
            widgetRegistration = null;
        }
    }

    public Measurable<Power> getLastDemand() {
        return lastDemand;
    }

    public String getResourceId() {
        return config.resourceId();
    }

    @Override
    protected List<? extends ResourceMessage> startRegistration(PowerState state) {
        currentState = state;
        changedState = timeService.getTime();
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

    private UncontrolledUpdate createUncontrolledUpdate(PowerState state) {
        Measurable<Power> currentUsage = state.getCurrentUsage();
        CommodityMeasurables measurables = CommodityMeasurables.electricity(currentUsage);
        UncontrolledUpdate update = new UncontrolledMeasurement(getResourceId(),
                                                                changedState,
                                                                timeService.getTime(),
                                                                measurables);
        return update;
    }

    @Override
    protected List<? extends ResourceMessage> updatedState(PowerState state) {
        return Arrays.asList(createUncontrolledUpdate(state));

    }

    @Override
    protected ResourceControlParameters receivedAllocation(ResourceMessage message) {
        throw new AssertionError();
    }

}
