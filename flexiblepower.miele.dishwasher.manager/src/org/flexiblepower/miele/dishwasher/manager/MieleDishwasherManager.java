package org.flexiblepower.miele.dishwasher.manager;

import static javax.measure.unit.NonSI.HOUR;
import static javax.measure.unit.NonSI.KWH;

import java.util.Date;
import java.util.Map;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Energy;

import org.flexiblepower.miele.dishwasher.manager.MieleDishwasherManager.Config;
import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationProvider;
import org.flexiblepower.rai.Allocation;
import org.flexiblepower.rai.TimeShifterControlSpace;
import org.flexiblepower.rai.values.EnergyProfile;
import org.flexiblepower.ral.ResourceManager;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherControlParameters;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherDriver;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.time.TimeService;
import org.flexiblepower.ui.Widget;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = ResourceManager.class)
public class MieleDishwasherManager extends
                                   AbstractResourceManager<TimeShifterControlSpace, DishwasherState, DishwasherControlParameters> {
    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "dishwasher")
        String resourceId();
    }

    private final DishwasherWidget widget;

    public MieleDishwasherManager() {
        super(DishwasherDriver.class, TimeShifterControlSpace.class);
        widget = new DishwasherWidget(this);
    }

    private Config config;
    private ServiceRegistration<Widget> widgetRegistration;

    @Activate
    public void activate(Map<String, Object> properties, BundleContext bundleContext) {
        try {
            config = Configurable.createConfigurable(Config.class, properties);
            widgetRegistration = bundleContext.registerService(Widget.class, widget, null);
        } catch (RuntimeException ex) {
            logger.error("Error during activation of the MieleDishwasherManager", ex);
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
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    private volatile DishwasherState currentState;
    private volatile Date changedState;

    @Override
    public void consume(ObservationProvider<? extends DishwasherState> source,
                        Observation<? extends DishwasherState> observation) {
        DishwasherState state = observation.getValue();
        if (state.getStartTime() == null) {
            // nothing selected
            // TODO: send empty control space?
            currentState = null;
            changedState = timeService.getTime();
        } else if (!state.getStartTime().equals(currentState.getStartTime())) {
            currentState = state;
            changedState = timeService.getTime();

            publish(createControlSpace(state));
        } else {
            // TODO: can this be skipped?
            publish(createControlSpace(state));
        }
    }

    private TimeShifterControlSpace createControlSpace(DishwasherState info) {
        // Get values from driver
        String program = "";
        Date startTime = timeService.getTime();

        if (info.getStartTime() == null) {
            return null;
        }

        logger.debug("Program selected: " + info.getProgram());
        program = info.getProgram();
        startTime = info.getStartTime();

        // Create energy Profile
        Measurable<Energy> energy = Measure.valueOf(1, KWH);
        Measurable<Duration> duration = Measure.valueOf(1, HOUR);

        // Set Energy Profile
        if (program == "Energy Save") {
            energy = Measure.valueOf(1, KWH);
            duration = Measure.valueOf(1, HOUR);
        } else if (program == "Sensor Wash") {
            energy = Measure.valueOf(1, KWH);
            duration = Measure.valueOf(2, HOUR);
        } else {
            energy = Measure.valueOf(2, KWH);
            duration = Measure.valueOf(2, HOUR);
        }

        EnergyProfile energyProfile = EnergyProfile.create().add(duration, energy).build();

        // Set Start and Stop Time
        Date startAfter = changedState;
        Date startBefore = new Date(startTime.getTime());

        // Set Experation Time of Control Space
        Date validFrom = startAfter;
        Date validThru = startBefore;
        Date expirationTime = startBefore;

        return new TimeShifterControlSpace(config.resourceId(),
                                           validFrom,
                                           validThru,
                                           expirationTime,
                                           energyProfile,
                                           startBefore,
                                           startAfter);
    }

    @Override
    public void handleAllocation(Allocation allocation) {
        logger.debug("Allocation is received" + allocation.toString());

        if (allocation.getEnergyProfile().getTotalEnergy().compareTo(Measure.<Energy> zero()) > 0) {
            getDriver().setControlParameters(new DishwasherControlParameters() {
                @Override
                public boolean getStartProgram() {
                    return true;
                }
            });
        }
    }

    protected DishwasherState getCurrentState() {
        return currentState;
    }
}
