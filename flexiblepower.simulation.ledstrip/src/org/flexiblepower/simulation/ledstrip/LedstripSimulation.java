package org.flexiblepower.simulation.ledstrip;

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
import org.flexiblepower.driver.ledstrip.LedstripControlParameters;
import org.flexiblepower.driver.ledstrip.LedstripLevel;
import org.flexiblepower.driver.ledstrip.LedstripState;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.simulation.ledstrip.LedstripSimulation.Config;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Modified;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Port(name = "manager", accepts = LedstripControlParameters.class, sends = LedstripState.class)
@Component(designateFactory = Config.class, provide = Endpoint.class, immediate = true)
public class LedstripSimulation extends AbstractResourceDriver<LedstripState, LedstripControlParameters>implements
                                Runnable,
                                MqttCallback {
    private static final Logger logger = LoggerFactory.getLogger(LedstripSimulation.class);

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "ledstrip", description = "Resource identifier")
               String resourceId();

        @Meta.AD(deflt = "tcp://130.211.82.48:1883", description = "URL to the MQTT broker")
               String brokerUrl();

        @Meta.AD(deflt = "1", description = "Frequency with which updates will be sent out in seconds")
            int updateFrequency();

        @Meta.AD(deflt = "/FpaiLedStripRequest", description = "Mqtt request topic to zenobox")
               String ledstripMqttRequestTopic();
    }

    private MqttClient mqttClient;

    private ScheduledFuture<?> scheduledFuture;
    private Config config;
    private LedstripLevel ledstripLevel = new LedstripLevel();

    class State implements LedstripState {
        private final LedstripLevel ledstripLevel;

        public State(LedstripLevel ledstripLevel) {
            this.ledstripLevel = ledstripLevel;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public LedstripLevel getLedstripLevel() {
            return ledstripLevel;
        }

    }

    // *************MQTT CALLBACK METHODS START**********************
    @Override
    public void connectionLost(Throwable arg0) {
        try {
            if (!mqttClient.isConnected()) {
                mqttClient.connect();
                // mqttClient.subscribe(FPAI_PV_PANEL_RESPONSE);
            }
        } catch (MqttException e) {

        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken arg0) {

    }

    @Override
    public void messageArrived(String arg0, MqttMessage arg1) throws Exception {

        // if (arg0.equals(FPAI_PV_PANEL_RESPONSE)) {
        // demand = Double.valueOf(arg1.toString());
        // IsZenoboxResponded = true;
    }

    // *************MQTT CALLBACK METHODS END**********************

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) throws MqttException {
        try {
            config = Configurable.createConfigurable(Config.class, properties);

            if (mqttClient == null) {
                mqttClient = new MqttClient(config.brokerUrl(), UUID.randomUUID().toString());
                mqttClient.setCallback(this);
                mqttClient.connect();
            }
            scheduledFuture = fpContext.scheduleAtFixedRate(this,
                                                            Measure.valueOf(0, SI.SECOND),
                                                            Measure.valueOf(config.updateFrequency(), SI.SECOND));
            ledstripLevel.setLevel(0);

        } catch (RuntimeException ex) {
            logger.error("Error during initialization of the generator simulation: " + ex.getMessage(), ex);
            deactivate();
            throw ex;
        }
    }

    @Modified
    public void modify(BundleContext bundleContext, Map<String, Object> properties) {
        try {
            config = Configurable.createConfigurable(Config.class, properties);

        } catch (RuntimeException ex) {
            logger.error("Error during modification of the generator simulation: " + ex.getMessage(), ex);
            deactivate();
            throw ex;
        }
    }

    @Deactivate
    public void deactivate() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
    }

    @Override
    public void run() {
        LedstripState state = new State(ledstripLevel);
        logger.debug("Publishing state {}", state);
        publishState(state);
    }

    private FlexiblePowerContext fpContext;

    @Reference
    public void setContext(FlexiblePowerContext fpContext) {
        this.fpContext = fpContext;
    }

    private byte[] toByta(double data) {
        return toByta(Double.doubleToRawLongBits(data));
    }

    /*
     * private byte[] toByta(double[] data) { if (data == null) { return null; } byte[] byts = new byte[data.length *
     * 8]; for (int i = 0; i < data.length; i++) { System.arraycopy(toByta(data[i]), 0, byts, i * 8, 8); } return byts;
     * }
     */

    @Override
    protected void handleControlParameters(LedstripControlParameters controlParameters) {
        // Send values to the Zenobox
        ledstripLevel = controlParameters.getLevel();
        try {
            MqttMessage msg = new MqttMessage();
            Measurable<Power> a = ledstripLevel.getLevel();
            String dd = Double.toString(a.doubleValue(SI.WATT));

            msg.setPayload(dd.getBytes());

            mqttClient.publish(config.ledstripMqttRequestTopic(), msg);

        } catch (MqttException e) {
            logger.warn("Could not send message", e);
        }

    }

    public LedstripLevel getLedstripLevel() {
        return ledstripLevel;
    }
}
