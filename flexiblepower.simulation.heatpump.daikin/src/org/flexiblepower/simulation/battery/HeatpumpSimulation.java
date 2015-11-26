package org.flexiblepower.simulation.battery;

import static javax.measure.unit.NonSI.KWH;
import static javax.measure.unit.SI.WATT;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;
import javax.measure.quantity.Temperature;
import javax.measure.unit.SI;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.miele.refrigerator.driver.RefrigeratorDriver.State;
import org.flexiblepower.protocol.mielegateway.api.ActionResult;
import org.flexiblepower.ral.drivers.battery.BatteryControlParameters;
import org.flexiblepower.ral.drivers.battery.BatteryDriver;
import org.flexiblepower.ral.drivers.battery.BatteryMode;
import org.flexiblepower.ral.drivers.battery.BatteryState;
import org.flexiblepower.ral.drivers.heatpump.HeatpumpControlParameters;
import org.flexiblepower.ral.drivers.heatpump.HeatpumpDriver;
import org.flexiblepower.ral.drivers.heatpump.HeatpumpState;
import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorState;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.simulation.battery.HeatpumpSimulation.Config;
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
 * @author bafkrstulovic
 *
 */
@Component(designateFactory = Config.class, provide = Endpoint.class, immediate = true)
public class HeatpumpSimulation
                                extends AbstractResourceDriver<HeatpumpState, HeatpumpControlParameters>
                                implements
                                HeatpumpDriver,
                                Runnable,
                                MqttCallback {

    interface Config {

        @Meta.AD(deflt = "DaikinHeatpumpManager", description = "Unique resourceID")
               String resourceId();

        @Meta.AD(deflt = "18", description = "Minimum Temperature in C")
               double minimumTemperature();

        @Meta.AD(deflt = "20", description = "Maximum Temperature in C")
               double maximumTemperature();

        @Meta.AD(deflt = "tcp://130.211.82.48:1883", description = "URL to the MQTT broker")
               String brokerUrl();

        @Meta.AD(deflt = "/FpaiHeatpumpRequest", description = "Mqtt request topic to zenobox")
               String batteryMqttRequestTopic();

        @Meta.AD(deflt = "/FpaiHeatpumpResponse", description = "Mqtt response topic to zenobox")
               String batteryMqttResponseTopic();
    }

    static final class State implements HeatpumpState {

        private final boolean isConnected;
        private final Measurable<Temperature> currentTemperature;
        private final Measurable<Temperature> targetTemperature;
        private final Measurable<Temperature> minimumTemperature;
        private final boolean heatMode;

        public State(boolean isConnected,
                     Measurable<Temperature> currentTemperature,
                     Measurable<Temperature> targetTemperature,
                     Measurable<Temperature> minimumTemperature,
                     boolean heatMode) {
            this.isConnected = isConnected;
            this.currentTemperature = currentTemperature;
            this.targetTemperature = targetTemperature;
            this.minimumTemperature = minimumTemperature;
            this.heatMode = heatMode;
        }

        @Override
        public boolean isConnected() {
            return isConnected;
        }

        @Override
        public Measurable<Temperature> getCurrentTemperature() {
            return currentTemperature;
        }

        @Override
        public Measurable<Temperature> getTargetTemperature() {
            return targetTemperature;
        }

        @Override
        public Measurable<Temperature> getMinimumTemperature() {
            return minimumTemperature;
        }

        @Override
        public boolean getHeatMode() {
            return heatMode;
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(HeatpumpSimulation.class);

    private volatile State currentState;

    private MqttClient mqttClient;
    private Date lastUpdatedTime;
    private Config configuration;

    private ScheduledFuture<?> scheduledFuture;

    private ServiceRegistration<Widget> widgetRegistration;

    private HeatpumpWidget widget;

    private FlexiblePowerContext context;

    State getCurrentState() {
        return currentState;
    }

    @Reference
    public void setContext(FlexiblePowerContext context) {
        this.context = context;
        // lastUpdatedTime = context.currentTime();
    }

    @Activate
    public void activate(BundleContext context, Map<String, Object> properties) throws Exception {
        try {
            configuration = Configurable.createConfigurable(Config.class, properties);

            currentTemperature = Measure.valueOf(configuration.currentTemperature(), CELCIUS);
            targetTemperature = Measure.valueOf(configuration.targetTemperature(), CELCIUS);
            minimumTemperature = Measure.valueOf(configuration.minimumTemperature(), CELCIUS);
            maximumTemperature = Measure.valueOf(configuration.minimumTemperature(), CELCIUS);
            boolean mode = false;

            if (mqttClient == null) {
                mqttClient = new MqttClient(configuration.brokerUrl(), UUID.randomUUID().toString());
                mqttClient.setCallback(this);
                mqttClient.connect();

                mqttClient.subscribe(configuration.batteryMqttResponseTopic());
            }

            publishState(new State(currentState));

            scheduledFuture = this.context.scheduleAtFixedRate(this,
                                                               Measure.valueOf(0, SI.SECOND),
                                                               Measure.valueOf(configuration.updateInterval(),
                                                                               SI.SECOND));

            widget = new HeatpumpWidget(this);
            widgetRegistration = context.registerService(Widget.class, widget, null);
        } catch (Exception ex) {
            logger.error("Error during initialization of the battery simulation: " + ex.getMessage(), ex);
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
                mqttClient.subscribe(configuration.batteryMqttResponseTopic());
            }
        } catch (MqttException e) {

        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken arg0) {

    }

    @Override
    public void messageArrived(String arg0, MqttMessage arg1) throws Exception {

        if (arg0.equals(configuration.heatpumpMqttResponseTopic())) {
            logger.info("ZENODYS HEATPUMP : " + arg1.toString());

            if (lastUpdatedTime == null) {
                lastUpdatedTime = context.currentTime();
            }

            Date currentTime = context.currentTime();
            double durationSinceLastUpdate = (currentTime.getTime() - lastUpdatedTime.getTime()) / 1000.0; // in seconds
            lastUpdatedTime = currentTime;

            publishState(new State(currentTemperature));
        }
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

    @Modified
    public void modify(BundleContext context, Map<String, Object> properties) {
        try {
            configuration = Configurable.createConfigurable(Config.class, properties);

            currentTemperature = Measure.valueOf(configuration.currentTemperature(), CELCIUS);
            targetTemperature = Measure.valueOf(configuration.targetTemperature(), CELCIUS);
            minimumTemperature = Measure.valueOf(configuration.minimumTemperature(), CELCIUS);
            boolean mode = false;
        } catch (RuntimeException ex) {
            logger.error("Error during initialization of the heatpump simulation: " + ex.getMessage(), ex);
            deactivate();
            throw ex;
        }
    }

    @Override
    public synchronized void run() {
        State state = new State(currentTemperature, mode);
        logger.debug("Publishing state {}", state);
        publishState(state);
    }

    @Override
    protected void handleControlParameters(HeatpumpControlParameters controlParameters) {
        if (currentState == null) {
            // no valid state, queue this controlParameter
            controlParameterQueue = controlParameters;
        } else {
            // Execute the Control Parameter
            if (!currentState.heatMode && controlParameters.getHeatMode()) {
                // Turn heatingMode on!
                String turnHeatpumpOn = "true";
                logger.debug("Turning heating mode on");

                // Send values to the Zenobox
                try {
                    MqttMessage msg = new MqttMessage();
                    msg.setPayload(turnHeatpumpOn.getBytes());

                    mqttClient.publish(config.heatpumpMqttRequestTopic(), msg);

                } catch (MqttException e) {
                    logger.warn("Could not send message", e);
                }

             }

             logger.debug("Result of truning supercool mode on: " + actionResult.toString());

             // Invalidate the currentState
             currentState = null;

            } else if (currentState.heatMode && !controlParameters.getHeatMode()) {
                // Turn heatingMode off!
                String turnHeatpumpOn = "false";
                logger.debug("Turning heating mode on");

                // Send values to the Zenobox
                try {
                    MqttMessage msg = new MqttMessage();
                    msg.setPayload(turnHeatpumpOn.getBytes());

                    mqttClient.publish(config.heatpumpMqttRequestTopic(), msg);

                } catch (MqttException e) {
                    logger.warn("Could not send message", e);
                }

                // Invalidate the currentState
                currentState = null;

            } else {
                logger.debug("Received controlparameter with heating = " + controlParameters.getHeatMode()
                             + ", but that already is the state, ignoring...");
            }
    }

}
