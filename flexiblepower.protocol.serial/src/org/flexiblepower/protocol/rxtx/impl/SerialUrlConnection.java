package org.flexiblepower.protocol.rxtx.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

import org.flexiblepower.protocol.rxtx.Connection;
import org.flexiblepower.protocol.rxtx.ConnectionFactory;
import org.flexiblepower.protocol.rxtx.SerialConnectionOptions;
import org.flexiblepower.protocol.rxtx.SerialConnectionOptions.Baudrate;
import org.flexiblepower.protocol.rxtx.SerialConnectionOptions.Databits;
import org.flexiblepower.protocol.rxtx.SerialConnectionOptions.Parity;
import org.flexiblepower.protocol.rxtx.SerialConnectionOptions.Stopbits;

public class SerialUrlConnection extends URLConnection {

    public static Map<String, String> splitQuery(URL url) throws UnsupportedEncodingException {
        Map<String, String> query_pairs = new LinkedHashMap<String, String>();
        String query = url.getQuery();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                            URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
        }
        return query_pairs;
    }

    private static String getOrDefault(Map<String, String> query, String key, String dflt) {
        if (query.containsKey(key)) {
            return query.get(key);
        } else {
            return dflt;
        }
    }

    private final ConnectionFactory factory;
    private final String portName;
    private final SerialConnectionOptions options;

    protected SerialUrlConnection(URL url, ConnectionFactory factory) throws UnsupportedEncodingException,
                                                                     MalformedURLException {
        super(url);
        this.factory = factory;

        Map<String, String> query = splitQuery(url);
        try {
            Baudrate baudrate = Baudrate.valueOf("B" + getOrDefault(query, "baud", "9600"));
            Databits databits = Databits.valueOf("D" + getOrDefault(query, "databits", "8"));
            Stopbits stopbits = Stopbits.valueOf("S" + getOrDefault(query, "stopbits", "1"));
            Parity parity = Parity.valueOf(getOrDefault(query, "parity", "None"));

            options = new SerialConnectionOptions(baudrate, databits, stopbits, parity);
            portName = url.getHost();
        } catch (IllegalArgumentException ex) {
            throw new MalformedURLException("Incorrect configuration: " + ex.getMessage());
        }
    }

    private Connection connection;

    @Override
    public void connect() throws IOException {
        if (connection == null) {
            connection = factory.openSerialConnection(portName, options);
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        connect();
        return connection.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        connect();
        return connection.getOutputStream();
    }
}
