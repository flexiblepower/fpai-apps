package org.flexiblepower.simulation.rexManual;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ext.SimpleObservationProvider;
import org.flexiblepower.ral.ResourceControlParameters;
import org.flexiblepower.ral.drivers.uncontrolled.PowerState;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrollableDriver;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.simulation.rexManual.REXManualSimulation.Config;
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
public class REXManualSimulation extends AbstractResourceDriver<PowerState, ResourceControlParameters>
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

        public String getPrice() {
            return currentPrice;
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
        @Meta.AD(deflt = "1", description = "Delay between updates will be send out in seconds")
            int updateDelay();

        @Meta.AD(deflt = "REX", description = "Resource identifier")
               String resourceId();

    }

    private MqttClient mqttClient;
    public double demand = -0.01;
    private int updateDelay = 0;

    private static String currentPrice = "";
    private REXManualWidget widget;
    private ScheduledFuture<?> scheduledFuture;
    private ServiceRegistration<Widget> widgetRegistration;
    private Config config;
    private SimpleObservationProvider<PowerState> observationProvider;

    @Override
    public synchronized void run() {
        try {

            publishState(getCurrentState());
            observationProvider.publish(Observation.create(context.currentTime(),
                                                           getCurrentState()));

        } catch (Exception e) {
            logger.error("Error while running REXSimulation", e);
        }
    }

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) throws MqttException {

        try {
            config = Configurable.createConfigurable(Config.class, properties);
            updateDelay = config.updateDelay();

            observationProvider = SimpleObservationProvider.create(this, PowerState.class)
                                                           .observationOf("simulated rex")
                                                           .build();
            scheduledFuture = context.scheduleAtFixedRate(this,
                                                          Measure.valueOf(0, SI.SECOND),
                                                          Measure.valueOf(updateDelay, SI.SECOND));
            widget = new REXManualWidget(this);
            widgetRegistration = bundleContext.registerService(Widget.class, widget, null);
        } catch (RuntimeException ex) {
            logger.error("Error during initialization of the REX simulation: " + ex.getMessage(), ex);
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

    public void setPrice(final String price) {
        demand = Double.valueOf(price);
    }
}
