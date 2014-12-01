package org.flexiblepower.monitoring.csv;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.flexiblepower.monitoring.csv.Monitor.Config;
import org.flexiblepower.observation.ObservationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class)
public class Monitor {
    private static Logger logger = LoggerFactory.getLogger(Monitor.class);

    public interface Config {
        @Meta.AD(deflt = "/tmp/fpai/monitor", description = "The directory to which the CSV files will be written")
        String outputDirectory();
    }

    private final Map<ObservationProvider<?>, MonitoredProvider<?>> monitoredProviders;

    public Monitor() {
        monitoredProviders = new ConcurrentHashMap<ObservationProvider<?>, MonitoredProvider<?>>();
    }

    private final Map<ObservationProvider<?>, Map<String, Object>> unhandledObservationProviders = new HashMap<ObservationProvider<?>, Map<String, Object>>();
    private File dataDir;

    @Activate
    public void activate(Map<String, ?> properties) {
        Config config = Configurable.createConfigurable(Config.class, properties);

        dataDir = new File(config.outputDirectory());
        if (!dataDir.exists()) {
            if (!dataDir.mkdirs()) {
                logger.error("Could not create the directory [{}] for monitor data", dataDir);
                dataDir = null;
            }
        } else if (!dataDir.isDirectory() || !dataDir.canWrite()) {
            logger.error("The directory [{}] can not be written to", dataDir);
            dataDir = null;
        }

        if (dataDir != null) {
            logger.info("Started monitoring, output in [{}]", dataDir.getAbsolutePath());

            synchronized (unhandledObservationProviders) {
                for (Entry<ObservationProvider<?>, Map<String, Object>> entry : unhandledObservationProviders.entrySet()) {
                    addProvider(entry.getKey(), entry.getValue());
                }
                unhandledObservationProviders.clear();
            }
        }
    }

    @Reference(dynamic = true, multiple = true, optional = true)
    public <T> void addProvider(ObservationProvider<T> provider, Map<String, Object> properties) {
        if (dataDir != null) {
            logger.debug("Started monitoring of [{}]", provider);
            monitoredProviders.put(provider, new MonitoredProvider<T>(dataDir, provider, properties));
        } else {
            synchronized (unhandledObservationProviders) {
                if (dataDir == null) {
                    unhandledObservationProviders.put(provider, properties);
                } else {
                    addProvider(provider, properties);
                }
            }
        }
    }

    public void removeProvider(ObservationProvider<?> provider) {
        MonitoredProvider<?> storedProvider = monitoredProviders.get(provider);
        if (storedProvider != null) {
            storedProvider.close();
        }
        unhandledObservationProviders.remove(provider);
    }
}
