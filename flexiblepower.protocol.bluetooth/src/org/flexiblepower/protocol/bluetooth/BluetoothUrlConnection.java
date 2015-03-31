package org.flexiblepower.protocol.bluetooth;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import javax.microedition.io.StreamConnection;

import com.intel.bluetooth.MicroeditionConnector;

public class BluetoothUrlConnection extends URLConnection {
    public BluetoothUrlConnection(URL url) {
        super(url);
    }

    private StreamConnection connection;
    private InputStream is;
    private OutputStream os;

    public void connect() throws IOException {
        if (!connected) {
            connection = (StreamConnection) MicroeditionConnector.open(url.toString());
            connected = true;

            is = new FilterInputStream(connection.openInputStream()) {
                public void close() throws IOException {
                    if (connected) {
                        connection.close();
                        connected = false;
                        connection = null;
                    }
                }
            };
            os = new FilterOutputStream(connection.openOutputStream()) {
                public void close() throws IOException {
                    if (connected) {
                        connection.close();
                        connected = false;
                        connection = null;
                    }
                }
            };
        }
    }

    public InputStream getInputStream() throws IOException {
        connect();
        return is;
    }

    public OutputStream getOutputStream() throws IOException {
        connect();
        return os;
    }
}
