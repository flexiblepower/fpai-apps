package org.flexiblepower.simulation.heatpump.daikin;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Temperature;
import javax.measure.unit.SI;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.ral.drivers.heatpump.HeatpumpControlParameters;
import org.flexiblepower.ral.drivers.heatpump.HeatpumpState;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.simulation.heatpump.daikin.HeatpumpSimulation.Config;
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

@Port(name = "manager", accepts = HeatpumpControlParameters.class, sends = HeatpumpState.class)
@Component(designateFactory = Config.class, provide = Endpoint.class, immediate = true)
public class HeatpumpSimulation extends AbstractResourceDriver<HeatpumpState, HeatpumpControlParameters> implements
                                Runnable,
                                MqttCallback {
    private static final String Boolean = null;

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "DaikinHeatpump", description = "Resource identifier")
               String resourceId();

        @Meta.AD(deflt = "20.2", description = "initial temperature")
               double initialTemperature();

        @Meta.AD(deflt = "tcp://130.211.82.48:1883", description = "URL to the MQTT broker")
               String brokerUrl();

        @Meta.AD(deflt = "1", description = "Frequency with which updates will be sent out in seconds")
            int updateFrequency();

        @Meta.AD(deflt = "/HeinsbergHeatpumpModeRequest", description = "Mqtt request topic to zenobox")
               String heinsbergHeatpumpModeRequest();

        @Meta.AD(deflt = "/HeinsbergHeatpumpResponse", description = "Mqtt response topic to zenobox")
               String heinsbergHeatpumpResponse();

        @Meta.AD(deflt = "0", description = "Id of Unit")
               String unitId();
    }

    class State implements HeatpumpState {
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
    private HeatpumpControlParameters controlParameterQueue = null;

    private Date lastUpdatedTime;

    private MqttClient mqttClient;
    private Config config;

    private volatile State currentState;
    private ScheduledFuture<?> scheduledFuture;

    private ServiceRegistration<Widget> widgetRegistration;
    private HeatpumpWidget widget;
    private FlexiblePowerContext fpContext;

    // *************MQTT CALLBACK METHODS START**********************
    @Override
    public void connectionLost(Throwable arg0) {
        try {
            if (!mqttClient.isConnected()) {
                mqttClient.connect();
                mqttClient.subscribe(config.heinsbergHeatpumpResponse() + config.unitId());
            }
        } catch (MqttException e) {

        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken arg0) {

    }

    @Override
    public void messageArrived(String arg0, MqttMessage arg1) throws Exception {
        if (arg0.equals(config.heinsbergHeatpumpResponse() + config.unitId())) {
            logger.info("Heatpump : " + arg1.toString());

            if (lastUpdatedTime == null) {
                lastUpdatedTime = fpContext.currentTime();
            }
            Date currentTime = fpContext.currentTime();
            lastUpdatedTime = currentTime;

            String[] parts = arg1.toString().split(";");
            Integer hpMode = Integer.parseInt(parts[0]); // 0 = Cooling, 1 = Heating
            Integer hpState = Integer.parseInt(parts[1]); // 0 == Turned Off, 2 = Turned On
            String hpTemperature = parts[2];

            boolean heatMode = false;
            if (hpMode.equals(1)) {
                if (hpState.equals(1)) {
                    heatMode = true;
                }
            }

            Measurable<Temperature> currentTemp = Measure.valueOf(Double.valueOf(hpTemperature.replace(',', '.')),
                                                                  SI.CELSIUS);

            currentState = new State(true, currentTemp, null, null, heatMode);

            if (controlParameterQueue != null) {
                HeatpumpControlParameters temp = controlParameterQueue;
                controlParameterQueue = null;
                handleControlParameters(temp);
            }
        }
    }

    // *************MQTT CALLBACK METHODS END**********************

    State getCurrentState() {
        return currentState;
    }

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) throws MqttException {
        try {
            config = Configurable.createConfigurable(Config.class, properties);

            if (mqttClient == null) {
                mqttClient = new MqttClient(config.brokerUrl(), UUID.randomUUID().toString());
                mqttClient.setCallback(this);
                mqttClient.connect();

                mqttClient.subscribe(config.heinsbergHeatpumpResponse() + config.unitId());
            }

            Measurable<Temperature> initTemp = Measure.valueOf(config.initialTemperature(), null);
            currentState = new State(true, initTemp, null, null, false);
            publishState(currentState);

            scheduledFuture = fpContext.scheduleAtFixedRate(this,
                                                            Measure.valueOf(0, SI.SECOND),
                                                            Measure.valueOf(config.updateFrequency(), SI.SECOND));

            widget = new HeatpumpWidget(this);
            widgetRegistration = bundleContext.registerService(Widget.class, widget, null);

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
        logger.debug("Publishing state {}", currentState);
        publishState(currentState);
    }

    @Reference
    public void setContext(FlexiblePowerContext fpContext) {
        this.fpContext = fpContext;
    }

    @Override
    protected void handleControlParameters(HeatpumpControlParameters controlParameters) {
        // Send values to the Raspberry

        if (currentState == null) {
            // no valid state, queue this controlParameter
            controlParameterQueue = controlParameters;
        } else {
            // Execute the Control Parameter

            // TODO add currentTemp != targetTemp voor handmatig input van gebruiker
            // TODO warmtepomp kan ook coolen, zie code battery

            if (!currentState.heatMode && controlParameters.getHeatMode()) {
                // Turn heatMode on!
                logger.debug("Turning heat mode on");

                try {
                    MqttMessage msg = new MqttMessage();

                    String msgToSend = config.unitId() + ";turnon";
                    msg.setPayload(msgToSend.getBytes());

                    mqttClient.publish(config.heinsbergHeatpumpModeRequest(), msg);

                    logger.debug("Result of turning heat mode on: " + msgToSend);

                    // Invalidate the currentState
                    currentState = null;

                } catch (MqttException e) {
                    logger.warn("Could not send message", e);
                }

            } else if (currentState.heatMode && !controlParameters.getHeatMode()) {
                // Turn supercoolMode off!
                logger.debug("Turning heat mode off");

                try {
                    MqttMessage msg = new MqttMessage();

                    String msgToSend = config.unitId() + ";turnoff";
                    msg.setPayload(msgToSend.getBytes());

                    mqttClient.publish(config.heinsbergHeatpumpModeRequest(), msg);

                    logger.debug("Result of truning supercool mode off: " + msgToSend);

                    // Invalidate the currentState
                    currentState = null;

                } catch (MqttException e) {
                    logger.warn("Could not send message", e);
                }

            } else {
                logger.debug("Received controlparameter with heat = " + controlParameters.getHeatMode()
                             + ", but that already in that state, ignoring...");
            }
        }
    }

}
