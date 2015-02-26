package org.flexiblepower.monitoring.csv;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationConsumer;
import org.flexiblepower.observation.ObservationProvider;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MonitoredProvider<T> implements ObservationConsumer<T>, Closeable {
    private static final Logger logger = LoggerFactory.getLogger(MonitoredProvider.class);
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd-HHmmss");
    private static final String RESOURCE_ID = "resourceId";
    private static final String KEY_BASE = "org.flexiblepower.monitoring.type.";
    private static final long MONTH = 30L * 24 * 60 * 60 * 1000;
    private static final AtomicInteger FILE_COUNTER = new AtomicInteger();

    private final File dataDir;
    private final ObservationProvider<T> provider;
    private final List<String> keys;
    private final String filename;

    private BufferedWriter writer;
    private long lastTimestamp;

    public MonitoredProvider(File dataFile, ObservationProvider<T> provider, Map<String, ?> properties) {
        this.dataDir = dataFile;
        this.provider = provider;

        String name = provider.getClass().getCanonicalName();
        if (properties.containsKey(RESOURCE_ID)) {
            name = properties.get(RESOURCE_ID).toString();
        } else if (properties.containsKey(Constants.SERVICE_PID)) {
            name = properties.get(Constants.SERVICE_PID).toString();
        }
        this.filename = name;

        // Parse key strings for the observation
        List<String> keys = new ArrayList<String>(properties.size());
        for (String propKey : properties.keySet()) {
            if (propKey.startsWith(KEY_BASE)) {
                String key = propKey.substring(KEY_BASE.length());
                if (key.indexOf('.') < 0) {
                    keys.add(key);
                }
            }
        }
        this.keys = Collections.unmodifiableList(keys);

        provider.subscribe(this);

        logger.info("Started monitoring {} with keys {}", provider, keys);
    }

    private BufferedWriter getWriter(Date observationTime) {
        if (observationTime.getTime() < lastTimestamp || (observationTime.getTime() - lastTimestamp > MONTH)
            || writer == null) {
            closeWriter();

            // Open the file and save the writer
            StringBuilder filename = new StringBuilder();
            filename.append(String.format("%05d", FILE_COUNTER.incrementAndGet()))
                    .append(' ')
                    .append(this.filename)
                    .append(' ')
                    .append(DATE_FORMAT.format(observationTime))
                    .append(".csv");
            File file = new File(dataDir, filename.toString());
            try {
                writer = new BufferedWriter(new FileWriter(file));
            } catch (IOException e) {
                logger.error("Can not open file " + file.getAbsolutePath(), e);
                writer = null;
            }

            // Write header
            try {
                writer.append("timestamp,");
                for (int i = 0; i < keys.size(); i++) {
                    writer.append(keys.get(i).toString());
                    if (i < keys.size() - 1) {
                        writer.append(',');
                    }
                }
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                logger.error("Failed to write observation to file", e);
                writer = null;
            }

            // Start monitoring this provider
            provider.subscribe(this);
        }

        return writer;
    }

    private void closeWriter() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                logger.error("Failed to close writer", e);
            }
        }
    }

    @Override
    public synchronized void consume(ObservationProvider<? extends T> source, Observation<? extends T> observation) {
        logger.debug("Got observation from {}: {}", source, observation);

        BufferedWriter writer = getWriter(observation.getObservedAt());
        if (writer != null) {
            Map<String, Object> map = observation.getValueMap();
            try {
                writer.append(DATE_FORMAT.format(observation.getObservedAt()));
                for (String key : keys) {
                    writer.append(',');
                    Object value = map.get(key);
                    if (value != null) {
                        writer.append(value.toString());
                    }
                }
                writer.newLine();
                writer.flush();

                lastTimestamp = observation.getObservedAt().getTime();
            } catch (IOException e) {
                logger.error("Failed to write observation to file", e);
                close();
            }
        }
    }

    @Override
    public synchronized void close() {
        provider.unsubscribe(this);
        closeWriter();

        logger.info("Closed down monitoring for provider {}", provider);
    }
}
