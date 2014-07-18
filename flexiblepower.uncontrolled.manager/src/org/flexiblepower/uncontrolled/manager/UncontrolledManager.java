package org.flexiblepower.uncontrolled.manager;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.efi.uncontrolled.UncontrolledAllocation;
import org.flexiblepower.efi.uncontrolled.UncontrolledUpdate;
import org.flexiblepower.efi.util.CommodityProfile;
import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationProvider;
import org.flexiblepower.rai.comm.Allocation;
import org.flexiblepower.rai.values.Commodity;
import org.flexiblepower.ral.ResourceManager;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrolledControlParameters;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrolledDriver;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrolledState;
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.time.TimeService;
import org.flexiblepower.ui.Widget;
import org.flexiblepower.uncontrolled.manager.UncontrolledManager.Config;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = ResourceManager.class)
public class UncontrolledManager extends
                                AbstractResourceManager<Allocation, UncontrolledState, UncontrolledControlParameters> {

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
        super(UncontrolledDriver.class, UncontrolledControlSpace.class);
    }

    private TimeService timeService;
    private UncontrolledManagerWidget widget;
    private ServiceRegistration<Widget> widgetRegistration;
    private Measurable<Power> lastDemand;

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

    @Override
    public void handleAllocation(UncontrolledAllocation allocation) {
        // Unconstrained Allocations have a maximum power production or consumption for a
        // given duration.
        Date startTime = allocation.getStartTime();

        UncontrolledAllocation.Element element = allocation.getElement();

    }

    @Override
    public void consume(ObservationProvider<? extends UncontrolledState> source,
                        Observation<? extends UncontrolledState> observation) {
        UncontrolledState uncontrolledState = observation.getValue();
        lastDemand = uncontrolledState.getDemand();
        logger.debug("Received demand from " + source + " of " + uncontrolledState.getDemand());
        publish(constructControlSpace(observation.getValue()));
    }

    public Measurable<Power> getLastDemand() {
        return lastDemand;
    }

    public String getResourceId() {
        return config.resourceId();
    }

    private UncontrolledUpdate constructControlSpace(UncontrolledState uncontrolledState) {
        // Measure<Double, Energy> energy = Measure.valueOf(uncontrolledState.getDemand().doubleValue(SI.WATT) *
        // config.expirationTime(),
        // SI.JOULE);
        // EnergyProfile energyProfile = EnergyProfile.create()
        // .add(Measure.valueOf(config.expirationTime(), SI.SECOND), energy)
        // .build();
        // Date now = timeService.getTime();

        Timestamp startTime = new Timestamp(0);
        Timestamp endTime = new Timestamp(0);
        final Measurable<Duration> allocationDelay = Measure.valueOf(60, SI.SECOND);

        Map<Commodity, CommodityProfile> profiles = null;

        return new UncontrolledUpdate(config.resourceId(), startTime, endTime, allocationDelay, profiles);
    }
}
