package org.flexiblepower.monitoring.elasticsearch;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import org.flexiblepower.monitoring.elasticsearch.ElasticSearchConsumer.Config;
import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationConsumer;
import org.flexiblepower.observation.ObservationProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(immediate = true, provide = {}, designateFactory = Config.class)
public class ElasticSearchConsumer implements ObservationConsumer<Object> {
    private static final String KEY_OBSERVATION_ID = "org.flexiblepower.monitoring.observationOf";

    public ScheduledExecutorService scheduler;

    @Meta.OCD(description = "This configures the ObservationConsumer that sends all Observations to an MQTT bus")
    public static interface Config {
        @Meta.AD(deflt = "projectname", description = "URL to the MQTT broker")
        public String elasticSearchIndexName();

        @Meta.AD(deflt = "http://localhost:9200", description = "URL to the MQTT broker")
        public String elasticSearchServerURL();

        @Meta.AD(deflt = "15", description = "Write every x seconds to database")
        public int writeDelay();
    }

    @Reference
    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    public final Map<ObservationProvider<?>, String> providers = new ConcurrentHashMap<ObservationProvider<?>, String>();

    @Reference(dynamic = true, multiple = true, optional = true)
    public void addProvider(ObservationProvider<?> provider, Map<String, Object> properties) {
        String type = parseType(properties);
        if (type != null) {
            providers.put(provider, type);
            provider.subscribe(this);
        }
    }

    public void removeProvider(ObservationProvider<?> provider) {
        if (providers.remove(provider) != null) {
            provider.unsubscribe(this);
        }
    }

    private String parseType(Map<String, Object> properties) {
        String type = (String) properties.get(KEY_OBSERVATION_ID);
        if (type == null) {
            type = (String) properties.get(Constants.SERVICE_PID);
        }
        return type;
    }

    private ElasticSearchWriter writer;
    private Config config;

    @Activate
    public void activate(BundleContext context, Map<String, Object> properties) {
        config = Configurable.createConfigurable(Config.class, properties);
        writer = new ElasticSearchWriter(scheduler,
                                         config.writeDelay(),
                                         config.elasticSearchIndexName(),
                                         config.elasticSearchServerURL());
    }

    @Deactivate
    public void deactivate() {
        writer.close();
    }

    @Override
    public void consume(ObservationProvider<? extends Object> source, Observation<? extends Object> observation) {
        String type = providers.get(source);
        if (writer != null && type != null) {
            writer.addObservation(new Data(type, observation));
        }
    }
}
