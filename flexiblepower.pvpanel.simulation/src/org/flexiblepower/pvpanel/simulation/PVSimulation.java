package org.flexiblepower.pvpanel.simulation;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationProviderRegistrationHelper;
import org.flexiblepower.pvpanel.simulation.PVSimulation.Config;
import org.flexiblepower.ral.ResourceDriver;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrolledControlParameters;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrolledDriver;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrolledState;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
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

@Component(designateFactory = Config.class, provide = ResourceDriver.class)
public class PVSimulation extends AbstractResourceDriver<UncontrolledState, UncontrolledControlParameters> implements
                                                                                                          UncontrolledDriver,
                                                                                                          Runnable {
    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "pvpanel", description = "Resource identifier")
        String resourceId();

        @Meta.AD(deflt = "5", description = "Frequency in which updates will be send out in seconds")
        int updateFrequency();
    }

    private double demand = 0;
    private Weather weather = Weather.moon;

    private PVWidget widget;
    private ScheduledFuture<?> scheduledFuture;
    private ServiceRegistration<?> observationProviderRegistration;
    private ServiceRegistration<Widget> widgetRegistration;
    private Config config;

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        try {
            config = Configurable.createConfigurable(Config.class, properties);
            observationProviderRegistration = new ObservationProviderRegistrationHelper(this).observationType(UncontrolledState.class)
                                                                                             .observationOf(config.resourceId())
                                                                                             .observedBy(getClass().getName())
                                                                                             .register();
            scheduledFuture = schedulerService.scheduleAtFixedRate(this, 0, config.updateFrequency(), TimeUnit.SECONDS);
            widget = new PVWidget(this);
            widgetRegistration = bundleContext.registerService(Widget.class, widget, null);
        } catch (RuntimeException ex) {
            logger.error("Error during initialization of the PV simulation: " + ex.getMessage(), ex);
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
        if (observationProviderRegistration != null) {
            observationProviderRegistration.unregister();
            observationProviderRegistration = null;
        }
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
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

    @Override
    public synchronized void run() {
        try {
            demand = -weather.getProduction(Math.random());
            final Date currentDate = timeService.getTime();

            publish(new Observation<UncontrolledState>(currentDate, getCurrentState()));
        } catch (Exception e) {
            logger.error("Error while running PVSimulation", e);
        }
    }

    public Weather getWeather() {
        return weather;
    }

    public void setWeather(Weather weather) {
        this.weather = weather;
        run();
    }

    @Override
    public void setControlParameters(UncontrolledControlParameters resourceControlParameters) {
        // Nothing to control!
    }

    protected UncontrolledState getCurrentState() {
        final Measurable<Power> demand = Measure.valueOf(this.demand, SI.WATT);
        final Date currentTime = timeService.getTime();

        return new UncontrolledState() {

            @Override
            public boolean isConnected() {
                return true;
            }

            @Override
            public Measurable<Power> getDemand() {
                return demand;
            }

            @Override
            public Date getTime() {
                return currentTime;
            }
        };
    }
}
