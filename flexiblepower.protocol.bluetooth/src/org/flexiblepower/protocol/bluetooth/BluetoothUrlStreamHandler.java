package org.flexiblepower.protocol.bluetooth;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Hashtable;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.url.AbstractURLStreamHandlerService;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;

public class BluetoothUrlStreamHandler extends AbstractURLStreamHandlerService implements BundleActivator {
    private static final String PROTOCOL = "btspp";

    @Override
    public URLConnection openConnection(URL url) throws IOException {
        if (!PROTOCOL.equals(url.getProtocol())) {
            return new BluetoothUrlConnection(url);
        } else {
            throw new IOException("Unsupported protocol " + url.getProtocol());
        }
    }

    @Override
    public void start(BundleContext context) throws Exception {
        Hashtable<String, Object> properties = new Hashtable<String, Object>();
        properties.put(URLConstants.URL_HANDLER_PROTOCOL, PROTOCOL);
        context.registerService(URLStreamHandlerService.class, this, properties);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
    }
}
