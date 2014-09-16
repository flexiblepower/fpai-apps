package org.flexiblepower.uncontrolled.simulation;

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

import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.flexiblepower.ral.ResourceControlParameters;
import org.flexiblepower.ral.ResourceDriver;
import org.flexiblepower.ral.drivers.uncontrolled.PowerState;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrollableDriver;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.time.TimeService;
import org.flexiblepower.ui.Widget;
import org.flexiblepower.uncontrolled.simulation.UncontrolledSimulation.Config;
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
public class UncontrolledSimulation extends AbstractResourceDriver<PowerState, ResourceControlParameters> implements
                                                                                                         UncontrollableDriver,
                                                                                                         Runnable {
    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "pvpanel", description = "Resource identifier")
        String resourceId();

        @Meta.AD(deflt = "5", description = "Frequency in which updates will be send out in seconds")
        int updateFrequency();

        @Meta.AD(deflt = "0", description = "Generated Power when inverter is in stand by")
        double powerWhenStandBy();

        @Meta.AD(deflt = "200", description = "Generated Power when cloudy weather")
        int powerWhenCloudy();

        @Meta.AD(deflt = "1500", description = "Generated Power when sunny weather")
        int powerWhenSunny();

    }

    private double demand = -0.01;
    private double cloudy = 200;
    private double sunny = 1500;
    private Weather weather = Weather.moon;

    private UCWidget widget;
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

            observationProviderRegistration = new ObservationProviderRegistrationHelper(this).observationType(PowerState.class)
                                                                                             .observationOf(config.resourceId())
                                                                                             .observedBy(getClass().getName())
                                                                                             .register();
            scheduledFuture = schedulerService.scheduleAtFixedRate(this, 0, config.updateFrequency(), TimeUnit.SECONDS);
            widget = new UCWidget(this);
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
            demand = (int) -weather.getProduction(0, cloudy, sunny);
            final Date currentDate = timeService.getTime();

            if (demand < 0.1 && demand > -0.1 && config.powerWhenStandBy() > 0) {
                demand = config.powerWhenStandBy();
            }

            publishState(getCurrentState());
        } catch (Exception e) {
            logger.error("Error while uncontrolled simulation", e);
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
    public void handleControlParameters(ResourceControlParameters resourceControlParameters) {
        // Nothing to control!
    }

    double roundTwoDecimals(double d) {
        DecimalFormat twoDForm = new DecimalFormat("#.##");
        return Double.valueOf(twoDForm.format(d));
    }

    protected PowerStateImpl getCurrentState() {
        final Measurable<Power> demand = Measure.valueOf(this.demand, SI.WATT);
        final Date currentTime = timeService.getTime();

        return new PowerStateImpl(demand, currentTime);
    }
}
