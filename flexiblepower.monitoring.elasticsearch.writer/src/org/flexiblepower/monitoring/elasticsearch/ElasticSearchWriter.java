package org.flexiblepower.monitoring.elasticsearch;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticSearchWriter implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchWriter.class);

    private final ScheduledFuture<?> schedule;
    private final Queue<Data> observationsToWrite;
    private final String indexName;
    private final String baseUrl;
    private final CloseableHttpClient httpClient;

    public ElasticSearchWriter(ScheduledExecutorService scheduler, int delay, String indexName, String baseUrl) {
        schedule = scheduler.scheduleWithFixedDelay(this, delay / 2, delay, TimeUnit.SECONDS);
        observationsToWrite = new ConcurrentLinkedQueue<Data>();
        this.indexName = indexName; // "observations";
        this.baseUrl = baseUrl; // "http://192.168.1.60:9200";
        httpClient = HttpClientBuilder.create().build();

        try {
            createIndex();
        } catch (IOException ex) {
            logger.warn("I/O error while creating index", ex);
        }
    }

    private void createIndex() throws IOException {
        StringWriter sw = new StringWriter();
        DataWriter w = new DataWriter(sw);

        w.beginObject().name("mappings").beginObject();
        w.name("_default_").beginObject();
        w.name("properties").beginObject();
        w.name("@timestamp").beginObject();
        w.write("type", "date");
        w.write("format", "dateOptionalTime");
        w.endObject().endObject().endObject().endObject().endObject();

        w.close();

        String url = baseUrl + "/" + indexName + "/";
        HttpPut put = new HttpPut(url);
        put.setEntity(new StringEntity(sw.toString()));

        httpClient.execute(put).close();
        put.completed();
    }

    public void close() {
        schedule.cancel(false);

        try {
            httpClient.close();
        } catch (IOException e) {
        }
    }

    public void addObservation(Data data) {
        observationsToWrite.add(data);
    }

    @Override
    public void run() {
        if (!observationsToWrite.isEmpty()) {
            String url = baseUrl + "/_bulk";
            HttpPut httpPut = new HttpPut(url);
            String json = generateContent();
            httpPut.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));

            logger.debug("Bulk write to {} ({} characters)", url, json.length());

            try {
                CloseableHttpResponse response = httpClient.execute(httpPut);
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode < 200 || statusCode >= 400) {
                    throw new IOException("Statuscode: " + statusCode);
                }
                jsonToSend = null;
                response.close();
            } catch (IOException ex) {
                logger.warn("I/O error while writing to ElasticSearch: " + ex.getMessage(), ex);
            }
            httpPut.completed();
        } else {
            logger.debug("No observations to write");
        }
    }

    private transient volatile String jsonToSend = null;

    private synchronized String generateContent() {
        try {
            StringWriter sw = new StringWriter();
            if (jsonToSend != null) {
                sw.append(jsonToSend);
            }
            DataWriter w = new DataWriter(sw);

            Data data = null;
            while ((data = observationsToWrite.poll()) != null) {
                // Write create action with the index and type
                w.beginObject().name("create").beginObject();
                w.name("_index").value(indexName);
                w.name("_type").value(data.getType());
                w.endObject().endObject();
                sw.append('\n');

                w.beginObject();
                w.name("@timestamp").value(data.getObservation().getObservedAt());
                for (Entry<String, Object> entry : data.getObservation().getValueMap().entrySet()) {
                    w.write(entry.getKey(), entry.getValue());
                }
                w.endObject();
                sw.append('\n');
            }

            w.close();
            return jsonToSend = sw.toString();
        } catch (IOException ex) {
            // Shouldn't be possible when using a StringWriter
            throw new AssertionError("I/O error while using a StringWriter: " + ex.getMessage());
        }
    }
}
