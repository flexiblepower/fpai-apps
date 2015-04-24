package org.flexiblepower.uncontrolled.simulation;

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

@Component(designateFactory = Config.class, provide = Endpoint.class, immediate = true)
public class UncontrolledSimulation extends AbstractResourceDriver<PowerState, ResourceControlParameters> implements
                                                                                                         UncontrollableDriver,
                                                                                                         Runnable {
    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "pvpanel", description = "Resource identifier")
        String resourceId();

        @Meta.AD(deflt = "5", description = "Frequency in which updates will be send out in seconds")
        int updateFrequency();

        @Meta.AD(deflt = "0", description = "Generated Power when device is off")
        double powerWhenOff();

        @Meta.AD(deflt = "200", description = "Generated Power when TV is on")
        int powerWhenTV();

        @Meta.AD(deflt = "1500", description = "Generated Power when Espresso Machine is On")
        int powerWhenEspresso();

    }

    private double demand = -0.01;
    private double tv = 200;
    private double coffee = 1500;
    private Devices device = Devices.off;

    private UCWidget widget;
    private ScheduledFuture<?> scheduledFuture;
    private ServiceRegistration<Widget> widgetRegistration;
    private Config config;

    private SimpleObservationProvider<PowerState> observationProvider;

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        try {
            config = Configurable.createConfigurable(Config.class, properties);

            tv = config.powerWhenTV();
            coffee = config.powerWhenEspresso();

            observationProvider = SimpleObservationProvider.create(this, PowerState.class)
                                                           .observationOf("simulated uncontrolled")
                                                           .build();

            scheduledFuture = context.scheduleAtFixedRate(this,
                                                          Measure.valueOf(0, SI.SECOND),
                                                          Measure.valueOf(config.updateFrequency(), SI.SECOND));

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

            tv = config.powerWhenTV();
            coffee = config.powerWhenEspresso();

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
            demand = (int) device.getProduction(0, tv, coffee);

            if (demand < 0.1 && demand > -0.1 && config.powerWhenOff() > 0) {
                demand = config.powerWhenOff();
            }

            publishState(getCurrentState());
            observationProvider.publish(new Observation<PowerState>(context.currentTime(), getCurrentState()));
        } catch (Exception e) {
            logger.error("Error while uncontrolled simulation", e);
        }
    }

    public Devices getDevice() {
        return device;
    }

    public void setDevice(Devices weather) {
        device = weather;
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
        final Date currentTime = context.currentTime();
        return new PowerStateImpl(demand, currentTime);
    }
}
