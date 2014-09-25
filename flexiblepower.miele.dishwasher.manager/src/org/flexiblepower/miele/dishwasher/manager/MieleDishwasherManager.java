package org.flexiblepower.miele.dishwasher.manager;

import static javax.measure.unit.NonSI.HOUR;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.efi.TimeShifterResourceManager;
import org.flexiblepower.efi.timeshifter.SequentialProfile;
import org.flexiblepower.efi.timeshifter.SequentialProfileAllocation;
import org.flexiblepower.efi.timeshifter.TimeShifterAllocation;
import org.flexiblepower.efi.timeshifter.TimeShifterRegistration;
import org.flexiblepower.efi.timeshifter.TimeShifterUpdate;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.miele.dishwasher.manager.MieleDishwasherManager.Config;
import org.flexiblepower.rai.AllocationRevoke;
import org.flexiblepower.rai.ResourceMessage;
import org.flexiblepower.rai.values.CommodityForecast;
import org.flexiblepower.rai.values.CommodityForecast.Builder;
import org.flexiblepower.rai.values.CommoditySet;
import org.flexiblepower.rai.values.UncertainMeasure;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherControlParameters;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.time.TimeService;
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
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = Endpoint.class, immediate = true)
@Port(name = "driver", sends = DishwasherControlParameters.class, accepts = DishwasherState.class)
public class MieleDishwasherManager extends
                                   AbstractResourceManager<DishwasherState, DishwasherControlParameters> implements
                                                                                                        TimeShifterResourceManager {
    private static final Logger log = LoggerFactory.getLogger(MieleDishwasherManager.class);

    private final class DishwasherControlParametersImpl implements DishwasherControlParameters {
        private final Date startTime;
        private final String program;

        private DishwasherControlParametersImpl(Date startTime, String program) {
            this.startTime = startTime;
            this.program = program;
        }

        @Override
        public Date getStartTime() {
            return startTime;
        }

        @Override
        public String getProgram() {
            return program;
        }
    }

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "MieleDishwasherManager", description = "Unique resourceID")
        String resourceId();

        @Meta.AD(deflt = "true", description = "Whether to show the widget")
        boolean showWidget();
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    private DishwasherState currentState;
    private Date changedStateTime;
    private TimeShifterUpdate currentUpdate;
    private TimeShifterAllocation currentAllocation;
    private Measure<Integer, Duration> allocationDelay;
    private ServiceRegistration widgetRegistration;
    private MieleDishwasherWidget widget;
    private Config configuration;

    @Override
    protected List<? extends ResourceMessage> startRegistration(DishwasherState state) {
        currentState = state;
        changedStateTime = timeService.getTime();
        allocationDelay = Measure.valueOf(5, SI.SECOND);

        TimeShifterRegistration reg = new TimeShifterRegistration(configuration.resourceId(),
                                                                  changedStateTime,
                                                                  allocationDelay,
                                                                  CommoditySet.onlyElectricity);
        TimeShifterUpdate update = createControlSpace(state);
        log.debug("sending timeshifter registration and update");
        return Arrays.asList(reg, update);
    }

    @Override
    protected List<? extends ResourceMessage> updatedState(DishwasherState state) {
        if (state.equals(currentState)) {
            return Collections.emptyList();
        } else {
            currentState = state;
            changedStateTime = timeService.getTime();
            TimeShifterUpdate update = createControlSpace(state);
            log.debug("sending timeshifter update");
            return Arrays.asList(update);
        }
    }

    @Override
    protected DishwasherControlParameters receivedAllocation(ResourceMessage message) {
        log.debug("Allocation received");
        if (message instanceof TimeShifterAllocation) {
            log.debug("TimeShifterAllocation received");
            currentAllocation = (TimeShifterAllocation) message;
            List<SequentialProfileAllocation> sequentialProfileAllocations = currentAllocation.getSequentialProfileAllocation();
            if (!sequentialProfileAllocations.isEmpty() && currentState != null && currentState.getProgram() != null) {

                log.debug("returning new controlparameters");
                SequentialProfileAllocation sequentialProfileAllocation = sequentialProfileAllocations.get(0);

                // publishToController(createControlSpace());
                if (sequentialProfileAllocation.getStartTime() != null) {
                    return new DishwasherControlParametersImpl(sequentialProfileAllocation.getStartTime(),
                                                               currentState.getProgram());
                } else {
                    log.error("starttime in sequential profileAllocation is null! Ignoring");
                }
            }

        } else if (message instanceof AllocationRevoke) {
            log.debug("Revocation message received");
            if (currentAllocation != null) {
                return new DishwasherControlParametersImpl(null, null);
            }
        } else {
            log.warn("Unknown message type received: {}", message.getClass());
        }
        return null;
    }

    private TimeShifterUpdate createControlSpace(DishwasherState info) {
        logger.debug("Program selected: " + info.getProgram());
        String program = info.getProgram();

        // Create energy Profile
        UncertainMeasure<Power> energy1 = null;
        UncertainMeasure<Duration> duration1 = null;
        UncertainMeasure<Power> energy2 = null;
        UncertainMeasure<Duration> duration2 = null;
        UncertainMeasure<Power> energy3 = null;
        UncertainMeasure<Duration> duration3 = null;

        Builder forecastBuilder = CommodityForecast.create();

        // Set Energy Profile
        if (program.equalsIgnoreCase("Energy Save")) {
            energy1 = new UncertainMeasure<Power>(1000, SI.WATT);
            duration1 = new UncertainMeasure<Duration>(1, HOUR);
            forecastBuilder.duration(duration1);
            forecastBuilder.electricity(energy1);
            forecastBuilder.next();
            energy2 = new UncertainMeasure<Power>(500, SI.WATT);
            duration2 = new UncertainMeasure<Duration>(1, HOUR);
            forecastBuilder.duration(duration2);
            forecastBuilder.electricity(energy2);
            forecastBuilder.next();

        } else if (program.equalsIgnoreCase("Sensor Wash")) {
            energy1 = new UncertainMeasure<Power>(1000, SI.WATT);
            duration1 = new UncertainMeasure<Duration>(2, HOUR);
            forecastBuilder.duration(duration1);
            forecastBuilder.electricity(energy1);
            forecastBuilder.next();
            energy2 = new UncertainMeasure<Power>(1500, SI.WATT);
            duration2 = new UncertainMeasure<Duration>(0.5, HOUR);
            forecastBuilder.duration(duration2);
            forecastBuilder.electricity(energy2);
            forecastBuilder.next();
            energy3 = new UncertainMeasure<Power>(500, SI.WATT);
            duration3 = new UncertainMeasure<Duration>(0.3, HOUR);
            forecastBuilder.duration(duration3);
            forecastBuilder.electricity(energy3);
            forecastBuilder.next();
        } else {
            energy1 = new UncertainMeasure<Power>(2000, SI.WATT);
            duration1 = new UncertainMeasure<Duration>(3, HOUR);
            forecastBuilder.duration(duration1);
            forecastBuilder.electricity(energy1);
            forecastBuilder.next();
            energy2 = new UncertainMeasure<Power>(1000, SI.WATT);
            duration2 = new UncertainMeasure<Duration>(1, HOUR);
            forecastBuilder.duration(duration2);
            forecastBuilder.electricity(energy2);
            forecastBuilder.next();
        }

        CommodityForecast forecast = forecastBuilder.build();
        UncertainMeasure<Duration> maxInterval = new UncertainMeasure<Duration>(0, HOUR);

        return new TimeShifterUpdate(configuration.resourceId(),
                                     changedStateTime,
                                     changedStateTime,
                                     info.getLatestStartTime(),
                                     new SequentialProfile(0,
                                                           maxInterval,
                                                           forecast));
    }

    protected DishwasherState getCurrentState() {
        return currentState;
    }

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {

        configuration = Configurable.createConfigurable(Config.class, properties);
        if (configuration.showWidget()) {
            log.debug("Adding Miele dishwasher widget");
            widget = new MieleDishwasherWidget(this);
            widgetRegistration = bundleContext.registerService(Widget.class, widget, null);
        }
        log.debug("Activated");
    }

    @Deactivate
    public void deactivate() {
        widgetRegistration.unregister();
    }

}
