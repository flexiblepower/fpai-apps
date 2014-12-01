package org.flexiblepower.monitoring.mqtt;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.flexiblepower.monitoring.mqtt.ObservationSender.Config;
import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationConsumer;
import org.flexiblepower.observation.ObservationProvider;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

@Component(provide = {}, designateFactory = Config.class)
public class ObservationSender implements ObservationConsumer<Object>, MqttCallback {
    private static final Logger log = LoggerFactory.getLogger(ObservationSender.class);

    private static final String KEY_OBSERVATION_ID = "org.flexiblepower.monitoring.observationOf";

    @Meta.OCD(description = "This configures the ObservationConsumer that sends all Observations to an MQTT bus")
    public static interface Config {
        @Meta.AD(deflt = "tcp://broker.labsgn.tno.nl:1883", description = "URL to the MQTT broker")
        public String brokerUrl();
    }

    private final Map<ObservationProvider<?>, String> topics = new HashMap<ObservationProvider<?>, String>();
    private final GsonBuilder gsonBuilder;

    public ObservationSender() {
        gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Observation.class, new JsonSerializer<Observation<?>>() {
            @Override
            public JsonElement serialize(Observation<?> observation, Type type, JsonSerializationContext context) {
                JsonObject obj = new JsonObject();
                obj.addProperty("t", observation.getObservedAt().getTime());
                Map<String, Object> valueMap = observation.getValueMap();
                for (Entry<String, Object> entry : valueMap.entrySet()) {
                    JsonElement value = context.serialize(entry.getValue());
                    obj.add(entry.getKey(), value);
                }
                return obj;
            }
        });
    }

    @Reference(optional = true, dynamic = true, multiple = true)
    public void addObservationProvider(ObservationProvider<?> provider, Map<String, Object> properties) {
        if (properties.containsKey(KEY_OBSERVATION_ID)) {
            topics.put(provider,
                       "fpai/" + properties.get(KEY_OBSERVATION_ID).toString().replace('.', '-').replace(' ', '_'));
            provider.subscribe(this);
        }
    }

    public void removeObservationProvider(ObservationProvider<?> provider) {
        if (topics.remove(provider) != null) {
            provider.unsubscribe(this);
        }
    }

    private Config config;

    private MqttClient mqttClient;

    @Activate
    public void activate(BundleContext context, Map<String, Object> properties) {
        config = Configurable.createConfigurable(ObservationSender.Config.class, properties);
        log.info("Starting ObservationSender for url: " + config.brokerUrl());
        try {
            mqttClient = new MqttClient(config.brokerUrl(), "ObservationSender");
            mqttClient.setCallback(this);
            mqttClient.connect();
        } catch (MqttException e) {
            log.warn("Could not connect to [" + config.brokerUrl() + "]", e);
            throw new RuntimeException(e);
        }
    }

    @Deactivate
    public void deactivate() {
        try {
            mqttClient.disconnect();
            mqttClient.close();
        } catch (MqttException e) {
            log.warn("Error while closing the MQTT connection", e);
            // Ignore for the rest of the program?
        }
    }

    @Override
    public void consume(ObservationProvider<? extends Object> source, Observation<? extends Object> observation) {
        String topic = topics.get(source);
        if (topic != null) {
            log.debug("Got {}, trying to send to {}", observation, topic);
            Gson gson = gsonBuilder.create();
            String json = gson.toJson(observation);
            MqttMessage msg = new MqttMessage(json.getBytes());
            try {
                if (!mqttClient.isConnected()) {
                    mqttClient.connect();
                }

                mqttClient.publish(topic, msg);
            } catch (MqttException e) {
                log.warn("Could not send message to topic [" + topic + "]", e);
            }
        }
    }

    @Override
    public void connectionLost(Throwable tr) {
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    @Override
    public void messageArrived(String topic, MqttMessage msg) throws Exception {
    }
}
