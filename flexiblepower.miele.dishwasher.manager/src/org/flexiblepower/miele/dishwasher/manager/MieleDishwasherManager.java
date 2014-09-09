package org.flexiblepower.miele.dishwasher.manager;

import static javax.measure.unit.NonSI.HOUR;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.efi.TimeShifterResourceManager;
import org.flexiblepower.efi.timeshifter.SequentialProfile;
import org.flexiblepower.efi.timeshifter.TimeShifterAllocation;
import org.flexiblepower.efi.timeshifter.TimeShifterAllocation.SequentialProfileAllocation;
import org.flexiblepower.efi.timeshifter.TimeShifterRegistration;
import org.flexiblepower.efi.timeshifter.TimeShifterUpdate;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.messaging.Ports;
import org.flexiblepower.miele.dishwasher.manager.MieleDishwasherManager.Config;
import org.flexiblepower.rai.comm.AllocationRevoke;
import org.flexiblepower.rai.comm.AllocationStatusUpdate;
import org.flexiblepower.rai.comm.ControlSpaceRevoke;
import org.flexiblepower.rai.comm.ResourceMessage;
import org.flexiblepower.rai.values.Commodity;
import org.flexiblepower.rai.values.CommodityForecast;
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
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = Endpoint.class, immediate = true)
@Ports({
        @Port(name = "driver", sends = DishwasherControlParameters.class, accepts = DishwasherState.class),
        @Port(name = "controller",
    sends = { TimeShifterRegistration.class,
              TimeShifterUpdate.class,
              AllocationStatusUpdate.class,
              ControlSpaceRevoke.class },
              accepts = { TimeShifterAllocation.class,
                         AllocationRevoke.class }
        )

})
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
        @Meta.AD(deflt = "true", description = "Whether to show the widget")
        boolean showWidget();
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    private DishwasherState currentState;
    private Date changedState;
    private TimeShifterUpdate currentUpdate;
    private TimeShifterAllocation currentAllocation;
    private Measure<Integer, Duration> allocationDelay;
    private ServiceRegistration widgetRegistration;
    private MieleDishwasherWidget widget;
    private Config configuration;

    @Override
    protected List<? extends ResourceMessage> startRegistration(DishwasherState state) {
        currentState = state;
        changedState = timeService.getTime();
        allocationDelay = Measure.valueOf(5, SI.SECOND);

        TimeShifterRegistration reg = new TimeShifterRegistration(null,
                                                                  changedState,
                                                                  allocationDelay,
                                                                  Commodity.Set.onlyElectricity);
        TimeShifterUpdate update = createControlSpace(state);
        return Arrays.asList(reg, update);
    }

    @Override
    protected List<? extends ResourceMessage> updatedState(DishwasherState state) {
        if (state.equals(currentState)) {
            return Collections.emptyList();
        } else {
            currentState = state;
            changedState = timeService.getTime();
            TimeShifterUpdate update = createControlSpace(state);
            return Arrays.asList(update);
        }
    }

    @Override
    protected DishwasherControlParameters receivedAllocation(ResourceMessage message) {
        if (message instanceof TimeShifterAllocation) {
            currentAllocation = (TimeShifterAllocation) message;
            if (currentUpdate != null && currentAllocation.getControlSpaceUpdateId()
                    .equals(currentUpdate.getResourceMessageId())) {
                List<SequentialProfileAllocation> sequentialProfileAllocations = currentAllocation.getSequentialProfileAllocation();
                if (!sequentialProfileAllocations.isEmpty()) {
                    SequentialProfileAllocation sequentialProfileAllocation = sequentialProfileAllocations.get(0);
                    return new DishwasherControlParametersImpl(sequentialProfileAllocation.getStartTime(),
                                                               currentState.getProgram());
                }
            } else {
                currentAllocation = null;
            }
        } else if (message instanceof AllocationRevoke) {
            if (currentAllocation != null) {
                return new DishwasherControlParametersImpl(null, null);
            }
        } else {
            log.warn("Unknown message type received: {}", message.getClass());
        }
        return null;
    }

    private TimeShifterUpdate createControlSpace(DishwasherState info) {
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
        UncertainMeasure<Power> energy = new UncertainMeasure<Power>(1000, SI.WATT);
        UncertainMeasure<Duration> duration = new UncertainMeasure<Duration>(1, HOUR);

        // Set Energy Profile
        if (program == "Energy Save") {
        } else if (program == "Sensor Wash") {
            duration = new UncertainMeasure<Duration>(2, HOUR);
        } else {
            energy = new UncertainMeasure<Power>(330, SI.WATT);
            duration = new UncertainMeasure<Duration>(2, HOUR);
        }

        // Set Start and Stop Time
        Date startBefore = new Date(startTime.getTime());

        CommodityForecast<Energy, Power> forecast = CommodityForecast.create(Commodity.ELECTRICITY)
                .add(duration, energy)
                .build();
        return new TimeShifterUpdate(null,
                                     changedState,
                                     changedState,
                                     allocationDelay,
                                     startBefore,
                                     Arrays.asList(new SequentialProfile(0,
                                                                         duration,
                                                                         new CommodityForecast.Map(forecast, null))));
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

}
