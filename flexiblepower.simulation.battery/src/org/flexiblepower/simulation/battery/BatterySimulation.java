
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
import javax.measure.unit.SI;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.ral.drivers.battery.BatteryControlParameters;
import org.flexiblepower.ral.drivers.battery.BatteryDriver;
import org.flexiblepower.ral.drivers.battery.BatteryMode;
import org.flexiblepower.ral.drivers.battery.BatteryState;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.simulation.battery.BatterySimulation.Config;
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
 * TODO Uit BatteryState weghalen van de charge en discharge efficiency
 *
 * @author bafkrstulovic
 *
 */
@Component(designateFactory = Config.class, provide = Endpoint.class, immediate = true)
public class BatterySimulation
                               extends AbstractResourceDriver<BatteryState, BatteryControlParameters>
                               implements
                               BatteryDriver,
                               Runnable,
                               MqttCallback {

    interface Config {
        @Meta.AD(deflt = "5", description = "Interval between state updates [s]")
             long updateInterval();

        @Meta.AD(deflt = "4", description = "Total capacity [kWh]")
               double totalCapacity();

        @Meta.AD(deflt = "0.5", description = "Initial state of charge (from 0 to 1)")
               double initialStateOfCharge();

        @Meta.AD(deflt = "1500", description = "Charge power [W]")
             long chargePower();

        @Meta.AD(deflt = "1500", description = "Discharge power [W]")
             long dischargePower();

        @Meta.AD(deflt = "0.9", description = "Charge efficiency (from 0 to 1)")
               double chargeEfficiency();

        @Meta.AD(deflt = "0.9", description = "Discharge efficiency (from 0 to 1)")
               double dischargeEfficiency();

        @Meta.AD(deflt = "50", description = "Self discharge power [W]")
             long selfDischargePower();

        @Meta.AD(deflt = "tcp://130.211.82.48:1883", description = "URL to the MQTT broker")
               String brokerUrl();

        @Meta.AD(deflt = "/HeinsbergBatteryResponse", description = "Mqtt response topic to zenobox")
               String heinsbergBatteryResponse();

        @Meta.AD(deflt = "/HeinsbergBatteryModeRequest", description = "Mqtt response topic to zenobox")
               String heinsbergBatteryModeRequest();
    }

    class State implements BatteryState {
        private final double stateOfCharge; // State of Charge is always within [0, 1] range.
        private final BatteryMode mode;

        public State(double stateOfCharge, BatteryMode mode) {
            // This is a quick fix. It would be better to throw an exception. This should be done later.
            if (stateOfCharge < 0.0) {
                stateOfCharge = 0.0;
            } else if (stateOfCharge > 1.0) {
                stateOfCharge = 1.0;
            }

            this.stateOfCharge = stateOfCharge;
            this.mode = mode;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public Measurable<Energy> getTotalCapacity() {
            return totalCapacityInKWh;
        }

        @Override
        public Measurable<Power> getChargeSpeed() {
            return chargeSpeedInWatt;
        }

        @Override
        public Measurable<Power> getDischargeSpeed() {
            return dischargeSpeedInWatt;
        }

        @Override
        public Measurable<Power> getSelfDischargeSpeed() {
            return selfDischargeSpeedInWatt;
        }

        @Override
        public double getChargeEfficiency() {
            return configuration.chargeEfficiency();
        }

        @Override
        public double getDischargeEfficiency() {
            return configuration.dischargeEfficiency();
        }

        @Override
        public Measurable<Duration> getMinimumOnTime() {
            return minTimeOn;
        }

        @Override
        public Measurable<Duration> getMinimumOffTime() {
            return minTimeOff;
        }

        @Override
        public double getStateOfCharge() {
            return stateOfCharge;
        }

        @Override
        public BatteryMode getCurrentMode() {
            return mode;
        }

        @Override
        public String toString() {
            return "State [stateOfCharge=" + stateOfCharge + ", mode=" + mode + "]";
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(BatterySimulation.class);
    private BatteryControlParameters controlParameterQueue = null;

    private Measurable<Power> dischargeSpeedInWatt;
    private Measurable<Power> chargeSpeedInWatt;
    private Measurable<Power> selfDischargeSpeedInWatt;
    private Measurable<Energy> totalCapacityInKWh;
    private Measurable<Duration> minTimeOn;
    private Measurable<Duration> minTimeOff;
    private BatteryMode mode;

    private MqttClient mqttClient;
    private Date lastUpdatedTime;
    private Config configuration;

    private volatile State currentState;

    private ScheduledFuture<?> scheduledFuture;

    private ServiceRegistration<Widget> widgetRegistration;

    private BatteryWidget widget;

    private FlexiblePowerContext context;

    @Reference
    public void setContext(FlexiblePowerContext context) {
        this.context = context;
        // lastUpdatedTime = context.currentTime();
    }

    @Activate
    public void activate(BundleContext context, Map<String, Object> properties) throws Exception {
        try {
            configuration = Configurable.createConfigurable(Config.class, properties);

            totalCapacityInKWh = Measure.valueOf(configuration.totalCapacity(), KWH);
            chargeSpeedInWatt = Measure.valueOf(configuration.chargePower(), WATT);
            dischargeSpeedInWatt = Measure.valueOf(configuration.dischargePower(), WATT);
            selfDischargeSpeedInWatt = Measure.valueOf(configuration.selfDischargePower(), WATT);
            double stateOfCharge = configuration.initialStateOfCharge();
            minTimeOn = Measure.valueOf(0, SI.SECOND);
            minTimeOff = Measure.valueOf(0, SI.SECOND);
            mode = BatteryMode.IDLE;

            if (mqttClient == null) {
                mqttClient = new MqttClient(configuration.brokerUrl(), UUID.randomUUID().toString());
                mqttClient.setCallback(this);
                mqttClient.connect();

                mqttClient.subscribe(configuration.heinsbergBatteryResponse());
            }

            publishState(new State(stateOfCharge, mode));

            scheduledFuture = this.context.scheduleAtFixedRate(this,
                                                               Measure.valueOf(0, SI.SECOND),
                                                               Measure.valueOf(configuration.updateInterval(),
                                                                               SI.SECOND));

            widget = new BatteryWidget(this);
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
                mqttClient.subscribe(configuration.heinsbergBatteryResponse());
            }
        } catch (MqttException e) {

        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken arg0) {

    }

    @Override
    public void messageArrived(String arg0, MqttMessage arg1) throws Exception {

        if (arg0.equals(configuration.heinsbergBatteryResponse())) {
            logger.info("Incoming Battery : " + arg1.toString());

            if (lastUpdatedTime == null) {
                lastUpdatedTime = context.currentTime();
            }

            Date currentTime = context.currentTime();
            lastUpdatedTime = currentTime;

            // Split message into SoC and Mode
            String[] parts = arg1.toString().split(";");
            String partSoc = parts[0];
            String partMode = parts[1];
            logger.info("Incoming Battery SoC:" + partSoc + "Battery Mode:" + partMode);

            double stateOfCharge = Double.valueOf(partSoc.replace(',', '.')) / 100;

            switch (Integer.valueOf(partMode)) {
            case 0:
                mode = BatteryMode.IDLE;
                break;
            case 1:
                mode = BatteryMode.CHARGE;
                break;
            case 2:
                mode = BatteryMode.DISCHARGE;
                break;
            }

            currentState = new State(stateOfCharge, mode);

        }
    }
          // *************MQTT CALLBACK METHODS END**********************

    State getCurrentState() {
        return currentState;
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

    @Modified
    public void modify(BundleContext context, Map<String, Object> properties) {
        try {
            configuration = Configurable.createConfigurable(Config.class, properties);

        } catch (RuntimeException ex) {
            logger.error("Error during initialization of the battery simulation: " + ex.getMessage(), ex);
            deactivate();
            throw ex;
        }
    }

    @Override
    public synchronized void run() {
        logger.debug("Publishing state {}", currentState);
        publishState(currentState);
    }

    @Override
    protected void handleControlParameters(BatteryControlParameters controlParameters) {

        // Send values to the Zenobox
        // ledstripLevel = controlParameters.getLevel();

        if (currentState == null) {
            // no valid state, queue this controlParameter
            // TODO DIT DOET NOG NIKS
            controlParameterQueue = controlParameters;
        } else {
            // Execute the Control Parameter

            // TODO add currentTEmp != targetTemp

            if (currentState.mode != controlParameters.getMode()) {
                // Charge battery
                logger.debug("Switch mode to" + controlParameters.getMode().toString());

                try {
                    MqttMessage msg = new MqttMessage();

                    String dd = controlParameters.getMode().toString();

                    msg.setPayload(dd.getBytes());

                    mqttClient.publish(configuration.heinsbergBatteryModeRequest(), msg);

                    logger.debug("Result of turning heat mode on: " + dd);

                    // Invalidate the currentState
                    currentState = null;

                } catch (MqttException e) {
                    logger.warn("Could not send message", e);
                }

            } else {
                logger.debug("Received controlparameter for battery = " + controlParameters.getMode()
                             + ", but already in that state, ignoring...");
            }
        }

    }

}
