package org.flexiblepower.pvpanel.simulation;

import java.text.DecimalFormat;
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
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
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
import aQute.bnd.annotation.component.Modified;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = ResourceDriver.class)
public class PVSimulation extends AbstractResourceDriver<UncontrolledState, UncontrolledControlParameters> implements
                                                                                                          UncontrolledDriver<UncontrolledState>,
                                                                                                          Runnable {
    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "pvpanel", description = "Resource identifier")
        String resourceId();

        @Meta.AD(deflt = "5", description = "Frequency in which updates will be send out in seconds")
        int updateFrequency();

        @Meta.AD(deflt = "200", description = "Generated Power when cloudy weather")
        int powerWhenCloudy();

        @Meta.AD(deflt = "1500", description = "Generated Power when sunny weather")
        int powerWhenSunny();

    }

    private double demand = 0;
    private double cloudy = 200;
    private double sunny = 1500;
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

            cloudy = config.powerWhenCloudy();
            sunny = config.powerWhenSunny();

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

    @Modified
    public void modify(BundleContext bundleContext, Map<String, Object> properties) {
        try {
            config = Configurable.createConfigurable(Config.class, properties);

            cloudy = config.powerWhenCloudy();
            sunny = config.powerWhenSunny();

        } catch (RuntimeException ex) {
            logger.error("Error during modification of the PV simulation: " + ex.getMessage(), ex);
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
            demand = (int) -weather.getProduction(Math.random(), cloudy, sunny);
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

    double roundTwoDecimals(double d) {
        DecimalFormat twoDForm = new DecimalFormat("#.##");
        return Double.valueOf(twoDForm.format(d));
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
