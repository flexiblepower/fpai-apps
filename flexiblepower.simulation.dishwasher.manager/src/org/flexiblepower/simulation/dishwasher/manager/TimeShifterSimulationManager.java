package org.flexiblepower.simulation.dishwasher.manager;

import static javax.measure.unit.NonSI.HOUR;
import static javax.measure.unit.NonSI.KWH;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Energy;
import javax.measure.unit.SI;

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
import org.flexiblepower.simulation.dishwasher.manager.TimeShifterSimulationManager.Config;
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
public class TimeShifterSimulationManager extends
                                         AbstractResourceManager<TimeShifterControlSpace, DishwasherState, DishwasherControlParameters> implements
                                                                                                                                       Runnable {
    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "timeshifter")
        String resourceId();
    }

    public TimeShifterSimulationManager() {
        super(DishwasherDriver.class, TimeShifterControlSpace.class);
        widget = new DishwasherWidget(this);
    }

    private Config config;
    private DishwasherWidget widget;
    private ServiceRegistration<Widget> widgetRegistration;
    private ScheduledFuture<?> scheduledFuture;

    @Activate
    public void activate(Map<String, Object> properties, BundleContext bundleContext) {

        try {
            config = Configurable.createConfigurable(Config.class, properties);
            widget = new DishwasherWidget(this);
            widgetRegistration = bundleContext.registerService(Widget.class, widget, null);

            scheduledFuture = schedulerService.scheduleAtFixedRate(this, 0, 1, java.util.concurrent.TimeUnit.MINUTES);

        } catch (RuntimeException ex) {
            logger.error("Error during activation of the MieleDishwasherManager", ex);
            deactivate();
            throw ex;
        }
    }

    @Deactivate
    public void deactivate() {
        widgetRegistration.unregister();
        widget = null;
    }

    private ScheduledExecutorService schedulerService;

    @Reference
    public void setSchedulerService(ScheduledExecutorService schedulerService) {
        this.schedulerService = schedulerService;
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    private volatile DishwasherState currentState;
    private volatile Date changedState;
    private final boolean ene = true;

    @Override
    public void run() {
        // TODO Auto-generated method stub

        // if (ene) {
        // Date now = new Date();
        // Date endTime = new Date(now.getTime() + 120 * 60000);
        // Measurable<Energy> energy = Measure.valueOf(0, KWH);
        // Measurable<Duration> duration = Measure.valueOf(1, HOUR);
        //
        // publish(createControlSpace(endTime, energy, duration));
        // ene = false;
        // } else {
        // Date now = new Date();
        // Date endTime = new Date(now.getTime() + 120 * 60000);
        // Measurable<Energy> energy = Measure.valueOf(1, KWH);
        // Measurable<Duration> duration = Measure.valueOf(1, HOUR);
        //
        // publish(createControlSpace(endTime, energy, duration));
        // ene = false;
        //
        // ene = true;
        // }

    }

    @Override
    public void consume(ObservationProvider<? extends DishwasherState> source,
                        Observation<? extends DishwasherState> observation) {
    }

    private TimeShifterControlSpace createControlSpace(Date endTime,
                                                       Measurable<Energy> energy,
                                                       Measurable<Duration> duration) {
        // Get values from driver
        String program = "";
        Date startTime = timeService.getTime();

        // Create energy Profile

        EnergyProfile energyProfile = EnergyProfile.create().add(duration, energy).build();

        // Set Start and Stop Time
        Date startAfter = new Date();
        Date startBefore = endTime;

        // Set Experation Time of Control Space
        Date validFrom = startAfter;

        Date validThru = new Date(startBefore.getTime() + (duration.longValue(SI.SECOND) * 1000)); // new
                                                                                                   // Date(1496430347966L);
        Date expirationTime = new Date(startBefore.getTime() + (duration.longValue(SI.SECOND) * 1000));

        TimeShifterControlSpace cs = new TimeShifterControlSpace(config.resourceId(),
                                                                 validFrom,
                                                                 validThru,
                                                                 expirationTime,
                                                                 energyProfile,
                                                                 startBefore,
                                                                 startAfter);

        logger.error("Control space sent: " + cs.toString());
        logger.error("Control space end time: " + startAfter.toString());
        logger.error("Control space end time: " + startBefore.toString());

        return cs;
    }

    @Override
    public void handleAllocation(Allocation allocation) {
        logger.error("Allocation is received" + allocation.toString());
        logger.error("Control Space id: " + allocation.getControlSpaceId());

        if (!running && allocation.getEnergyProfile().getTotalEnergy().compareTo(Measure.<Energy> zero()) > 0.1) {
            endTime = new Date();
            running = true;
        }
    }

    protected DishwasherState getCurrentState() {
        return currentState;
    }

    public Date endTime;
    public EnergyProfile energyProfile;
    public boolean running = false;

    public void startSimulation() {
        Date now = new Date();
        endTime = new Date(now.getTime() + 240 * 60000);
        energyProfile = createSimulationProfile();

        currentState = new DishwasherState() {

            @Override
            public boolean isConnected() {
                return running;
            }

            @Override
            public Date getStartTime() {
                return endTime;
            }

            @Override
            public String getProgram() {
                return "EcoFriendly ";
            }

            @Override
            public EnergyProfile getEnergyProfile() {
                return energyProfile;
            }
        };

        publish(createControlSpace(endTime, energyProfile.getTotalEnergy(), energyProfile.getDuration()));

    }

    public EnergyProfile createSimulationProfile() {
        Date now = new Date();
        Date endTime = new Date(now.getTime() + 60 * 60000);
        Measurable<Energy> energy = Measure.valueOf(1, KWH);
        Measurable<Duration> duration = Measure.valueOf(1, HOUR);
        EnergyProfile energyProfile = EnergyProfile.create().add(duration, energy).build();
        return energyProfile;
    }
}
