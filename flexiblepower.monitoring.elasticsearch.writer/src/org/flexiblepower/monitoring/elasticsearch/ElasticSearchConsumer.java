package org.flexiblepower.monitoring.elasticsearch;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationConsumer;
import org.flexiblepower.observation.ObservationProvider;
import org.osgi.framework.Constants;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;

@Component(immediate = true, provide = {})
public class ElasticSearchConsumer implements ObservationConsumer<Object> {
    private static final String KEY_OBSERVATION_ID = "org.flexiblepower.monitoring.observationOf";

    public ScheduledExecutorService scheduler;

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

    @Activate
    public void activate() {
        writer = new ElasticSearchWriter(scheduler, 30);
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
