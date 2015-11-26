package org.flexiblepower.example.zenobox;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measure;
import javax.measure.unit.SI;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.example.zenobox.Zenobox.Config;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.ui.Widget;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Modified;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

/**
 * This is an example of a driver implementation for a Zenobox example.
 */

@Component(designateFactory = Config.class, provide = Endpoint.class, immediate = true)
public class Zenobox
                    extends AbstractResourceDriver<ZenoboxState, ZenoboxControlParameters>
                                                                                          implements
                                                                                          Runnable,
                                                                                          MqttCallback
{

    interface Config {
        @Meta.AD(deflt = "2", description = "Interval between state updates [s]")
        long updateInterval();

        // @Meta.AD(deflt = "tcp://130.211.82.48:1883", description = "URL to the MQTT broker")
        @Meta.AD(deflt = "tcp://52.28.38.100:1883", description = "URL to the MQTT broker")
        public String
                brokerUrl();
    }

    // Class - implementation of ZenoboxState
    class State implements ZenoboxState {
        private final ZenoboxMode mMode;
        private final int mCurrentTemperature;

        public State(ZenoboxMode aMode, Integer aCurrentTemperature) {
            mMode = aMode;
            mCurrentTemperature = aCurrentTemperature;
        }

        @Override
        public boolean isConnected() {
            return (mMode == ZenoboxMode.ONLINE);
        }

        @Override
        public ZenoboxMode getCurrentMode() {
            return mMode;
        }

        @Override
        public String toString() {
            return "State [mode=" + mMode + "]";
        }

        @Override
        public int getCurrentTemperature() {
            // TODO Auto-generated method stub
            return mCurrentTemperature;
        }
    }

    // Some variables
    private static final Logger logger = LoggerFactory.getLogger(Zenobox.class);

    // private Measurable<Duration> minTimeOn;
    // private Measurable<Duration> minTimeOff;

    private ZenoboxMode mode;
    private int currentTemperature;

    private int offlineCounter = 0;
    private final int offlineCounterMax = 5;

    private Date lastUpdatedTime;
    private Config configuration;
    private MqttClient mqttClient;

    private ScheduledFuture<?> scheduledFuture;

    private ServiceRegistration<Widget> widgetRegistration;
    private ZenoboxWidget widget;

    private FlexiblePowerContext context;

    private final String FPAI_TEMPERATURE_SUBSCRIBE = "/FpaiTemperatureSubscribe";
    private final String FPAI_TEMPERATURE_PUBLISH = "/FpaiTemperaturePublish";
    private final String FPAI_LCD_PUBLISH = "/FpaiLcdPublish";

    private final String FPAI_LIGHT_OFF_PUBLISH = "/FpaiLightOffPublish";
    private final String FPAI_LIGHT_ON_PUBLISH = "/FpaiLightOnPublish";

    @Reference
    public void setContext(FlexiblePowerContext context) {
        this.context = context;
        lastUpdatedTime = context.currentTime();
    }

    @Activate
    public void activate(BundleContext context, Map<String, Object> properties) throws Exception {
        try {
            configuration = Configurable.createConfigurable(Config.class, properties);
            mode = ZenoboxMode.OFFLINE;

            // Configure MQTT
            mqttClient = new MqttClient(configuration.brokerUrl(), "ObservationSender");
            mqttClient.setCallback(this);
            mqttClient.connect();

            // Subscribe to number of topics
            mqttClient.subscribe(FPAI_TEMPERATURE_SUBSCRIBE);

            publishState(new State(mode, -999));

            scheduledFuture = this.context.scheduleAtFixedRate(this,
                                                               Measure.valueOf(0, SI.SECOND),
                                                               Measure.valueOf(configuration.updateInterval(),
                                                                               SI.SECOND));

            widget = new ZenoboxWidget(this);
            widgetRegistration = context.registerService(Widget.class, widget, null);
        } catch (Exception ex) {
            logger.error("Error during initialization of the battery simulation: " + ex.getMessage(), ex);
            deactivate();
            throw ex;
        }
    }

    @Deactivate
    public void deactivate() {

        // Disconnect MQTT
        try {
            mqttClient.disconnect();
            mqttClient.close();
        } catch (MqttException e) {
            logger.warn("Error while closing the MQTT connection", e);
            // Ignore for the rest of the program?
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

    @Modified
    public void modify(BundleContext context, Map<String, Object> properties) {
        try {
            configuration = Configurable.createConfigurable(Config.class, properties);
            mode = ZenoboxMode.OFFLINE;

        } catch (RuntimeException ex) {
            logger.error("Error during initialization of the battery simulation: " + ex.getMessage(), ex);
            deactivate();
            throw ex;
        }
    }

    @Override
    public synchronized void run() {
        // TODO Auto-generated method stub
        logger.info("RUN called");

        // decrement offline counter
        if (offlineCounter >= 0) {
            if (offlineCounter-- <= 0) {
                mode = ZenoboxMode.OFFLINE;
            }
        }

        // Generate MQTT messages
        try {
            MqttMessage msg = new MqttMessage();
            msg.setPayload((new String("GET")).getBytes());

            mqttClient.publish(FPAI_TEMPERATURE_PUBLISH, msg);
            mqttClient.publish(FPAI_LCD_PUBLISH, msg);
        } catch (MqttException e) {
            logger.warn("Could not send message", e);
        }
    }

    public void setLightState(boolean aState) {
        try {
            String msg_txt = (aState) ? "ON" : "OFF";

            MqttMessage msg = new MqttMessage();
            msg.setPayload(msg_txt.getBytes());

            if (aState) {
                mqttClient.publish(FPAI_LIGHT_ON_PUBLISH, msg);
            } else {
                mqttClient.publish(FPAI_LIGHT_OFF_PUBLISH, msg);
            }
        } catch (MqttException e) {
            logger.warn("Could not send message", e);
        }
    }

    @Override
    protected void handleControlParameters(ZenoboxControlParameters controlParameters) {
        // TODO Auto-generated method stub

    }

    protected State getCurrentState() {
        return new State(mode, currentTemperature);
    }

    // / ------ MQTT callback methods ------- ///

    @Override
    public void connectionLost(Throwable arg0) {
        // TODO Auto-generated method stub
        logger.info("Connection lost! Reconnecting...");

        try {
            if (!mqttClient.isConnected()) {
                mqttClient.connect();
                mqttClient.subscribe(FPAI_TEMPERATURE_SUBSCRIBE);
            }
        } catch (MqttException e) {
            logger.warn("Could not connect", e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken arg0) {
        // TODO Auto-generated method stub
        logger.info("MQTT Delivery complete");
    }

    @Override
    public void messageArrived(String arg0, MqttMessage arg1) throws Exception {
        // TODO Auto-generated method stub
        logger.info("MQTT message arvd.: " + arg0 + ", " + arg1.toString());

        if (arg0.equals(FPAI_TEMPERATURE_SUBSCRIBE)) {
            logger.info("Received " + FPAI_TEMPERATURE_SUBSCRIBE + ": " + arg1.toString());
        }

        // mode = (arg1.toString().equals("ONLINE")) ? ZenoboxMode.ONLINE : ZenoboxMode.OFFLINE;

        mode = ZenoboxMode.ONLINE;
        offlineCounter = offlineCounterMax;
        currentTemperature = Integer.parseInt(arg1.toString());

        logger.info(mode.toString());
        // publishState(new State(mode, Integer.parseInt(arg1.toString())));
    }
}
