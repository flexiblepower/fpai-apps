package org.flexiblepower.simulation.rexManual;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.messaging.Endpoint;
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
                                 Runnable,
                                 MqttCallback {

    public final static class PowerStateImpl implements PowerState {
        private final Measurable<Power> demand;
        private final double currentPrice;

        private final Date currentTime;

        private PowerStateImpl(Measurable<Power> demand, double price, Date currentTime) {
            this.demand = demand;
            currentPrice = price;
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

        public Double getPrice() {
            return currentPrice;
        }

        public Date getTime() {
            return currentTime;
        }

        @Override
        public String toString() {
            return "PowerStateImpl [demand=" + demand
                   + ", price="
                   + currentPrice
                   + ", currentTime="
                   + currentTime
                   + "]";
        }
    }

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "1", description = "Delay between updates will be send out in seconds")
            int updateDelay();

        @Meta.AD(deflt = "REX", description = "Resource identifier")
               String resourceId();

        @Meta.AD(deflt = "tcp://130.211.82.48:1883", description = "URL to the MQTT broker")
               String brokerUrl();

        @Meta.AD(deflt = "/HeinsbergREXRequest", description = "Mqtt request topic to zenobox")
               String heinsbergREXRequest();

        @Meta.AD(deflt = "/HeinsbergREXResponse", description = "Mqtt response topic to zenobox")
               String heinsbergREXResponse();

    }

    private volatile PowerStateImpl currentPowerState;
    private MqttClient mqttClient;
    private int updateDelay = 0;

    private REXManualWidget widget;
    private ScheduledFuture<?> scheduledFuture;
    private ServiceRegistration<Widget> widgetRegistration;
    private Config config;

    @Override
    public synchronized void run() {
        try {

            publishState(currentPowerState);

        } catch (Exception e) {
            logger.error("Error while running REXSimulation", e);
        }
    }

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) throws MqttException {

        try {
            config = Configurable.createConfigurable(Config.class, properties);
            updateDelay = config.updateDelay();

            if (mqttClient == null) {
                mqttClient = new MqttClient(config.brokerUrl(), UUID.randomUUID().toString());
                mqttClient.setCallback(this);
                mqttClient.connect();

                mqttClient.subscribe(config.heinsbergREXResponse());
            }

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

    // *************MQTT CALLBACK METHODS START**********************
    @Override
    public void connectionLost(Throwable arg0) {
        try {
            if (!mqttClient.isConnected()) {
                mqttClient.connect();
                mqttClient.subscribe(config.heinsbergREXResponse());
            }
        } catch (MqttException e) {

        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken arg0) {

    }

    @Override
    public void messageArrived(String arg0, MqttMessage arg1) throws Exception {

        if (arg0.equals(config.heinsbergREXResponse())) {
            String[] parts = arg1.toString().split(";");
            String price = parts[0];
            String power = parts[1];
            logger.info("REX : " + arg1.toString());
            double demand = Double.valueOf(power.replace(',', '.'));
            double currentPrice = Double.valueOf(price.replace(',', '.'));

        }
    }

    // *************MQTT CALLBACK METHODS END**********************

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
    protected void handleControlParameters(ResourceControlParameters controlParameters) {
        // Will never be called!
        throw new AssertionError();
    }

    double roundTwoDecimals(double d) {
        DecimalFormat twoDForm = new DecimalFormat("#.##");
        return Double.valueOf(twoDForm.format(d));
    }

    PowerStateImpl getCurrentState() {
        return currentPowerState;
    }

    public void setDemand(String demand) {
        // TODO Auto-generated method stub

        currentPowerState = new PowerStateImpl(Measure.valueOf(Double.valueOf(demand.replace(',', '.')), SI.WATT),
                                               0,
                                               context.currentTime());
    }

}
