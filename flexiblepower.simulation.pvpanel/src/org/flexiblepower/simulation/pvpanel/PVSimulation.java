package org.flexiblepower.simulation.pvpanel;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ext.SimpleObservationProvider;
import org.flexiblepower.ral.ResourceControlParameters;
import org.flexiblepower.ral.drivers.uncontrolled.PowerState;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrollableDriver;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.simulation.pvpanel.PVSimulation.Config;
import org.flexiblepower.ui.Widget;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = Endpoint.class, immediate = true)
public class PVSimulation extends AbstractResourceDriver<PowerState, ResourceControlParameters>
                                                                                               implements
                                                                                               UncontrollableDriver,
                                                                                               Runnable {

    public final static class PowerStateImpl implements PowerState {
        private final Measurable<Power> demand;

        private final Date currentTime;

        private PowerStateImpl(Measurable<Power> demand, Date currentTime) {
            this.demand = demand;
            this.currentTime = currentTime;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public Measurable<Power> getCurrentUsage() {
            return demand;
        }

        public Date getTime() {
            return currentTime;
        }

        @Override
        public String toString() {
            return "PowerStateImpl [demand=" + demand + ", currentTime=" + currentTime + "]";
        }
    }

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "5", description = "Delay between updates will be send out in seconds")
        int updateDelay();

        @Meta.AD(deflt = "0", description = "Generated Power when inverter is in stand by")
        double powerWhenStandBy();

        @Meta.AD(deflt = "200", description = "Generated Power when cloudy weather")
        int powerWhenCloudy();

        @Meta.AD(deflt = "1500", description = "Generated Power when sunny weather")
        int powerWhenSunny();

        @Meta.AD(deflt = "pvpanel", description = "Resource identifier")
        String resourceId();
    }

    private double demand = -0.01;
    private double cloudy = 200;
    private double sunny = 1500;
    private volatile Weather weather = Weather.moon;
    private int updateDelay = 0;

    private PVWidget widget;
    private ScheduledFuture<?> scheduledFuture;
    private ServiceRegistration<Widget> widgetRegistration;
    private Config config;
    private SimpleObservationProvider<PowerState> observationProvider;

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        try {
            config = Configurable.createConfigurable(Config.class, properties);
            updateDelay = config.updateDelay();
            cloudy = config.powerWhenCloudy();
            sunny = config.powerWhenSunny();

            observationProvider = SimpleObservationProvider.create(this, PowerState.class)
                                                           .observationOf("simulated pv")
                                                           .build();
            scheduledFuture = context.scheduleAtFixedRate(this,
                                                          Measure.valueOf(0, SI.SECOND),
                                                          Measure.valueOf(updateDelay, SI.SECOND));
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
        if (observationProvider != null) {
            observationProvider.close();
            observationProvider = null;
        }
        if (widgetRegistration != null) {
            widgetRegistration.unregister();
            widgetRegistration = null;
        }
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
    }

    private FlexiblePowerContext context;

    @Reference
    public void setContext(FlexiblePowerContext context) {
        this.context = context;
    }

    @Override
    public synchronized void run() {
        try {
            demand = -(weather.getProduction(Math.random(), cloudy, sunny));
            logger.info("new demand has been set to: {}", demand);

            if (demand < 0.1 && demand > -0.1 && config.powerWhenStandBy() > 0) {
                demand = config.powerWhenStandBy();
            }

            publishState(getCurrentState());
            observationProvider.publish(Observation.create(context.currentTime(), getCurrentState()));
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
    protected void handleControlParameters(ResourceControlParameters controlParameters) {
        // Will never be called!
        throw new AssertionError();
    }

    double roundTwoDecimals(double d) {
        DecimalFormat twoDForm = new DecimalFormat("#.##");
        return Double.valueOf(twoDForm.format(d));
    }

    protected PowerStateImpl getCurrentState() {
        return new PowerStateImpl(Measure.valueOf(demand, SI.WATT), context.currentTime());
    }
}
