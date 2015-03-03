package org.flexiblepower.monitoring.mysql.ui;

import javax.servlet.ServletException;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpActivator implements ServiceTrackerCustomizer<HttpService, HttpService> {
    private static final Logger logger = LoggerFactory.getLogger(HttpActivator.class);

    private final SQLServlet servlet;
    private final ServiceTracker<HttpService, HttpService> httpServerTracker;

    public HttpActivator(final BundleContext context, SQLServlet servlet) {
        this.servlet = servlet;

        httpServerTracker = new ServiceTracker<HttpService, HttpService>(context,
                                                                         HttpService.class,
                                                                         this);
        httpServerTracker.open();
    }

    public void close() {
        httpServerTracker.close();
    }

    @Override
    public HttpService addingService(ServiceReference<HttpService> reference) {
        HttpService httpService = httpServerTracker.addingService(reference);

        try {
            httpService.registerResources("/ui", "/web", null);
            httpService.registerServlet("/ui/sql", servlet, null, null);
        } catch (NamespaceException e) {
            logger.error("Namespace collision: " + e.getMessage(), e);
        } catch (ServletException e) {
            logger.error("Servlet error: " + e.getMessage(), e);
        }

        return httpService;
    }

    @Override
    public void modifiedService(ServiceReference<HttpService> reference, HttpService service) {
    }

    @Override
    public void removedService(ServiceReference<HttpService> reference, HttpService service) {
        service.unregister("/ui");
        service.unregister("/ui/sql");
        httpServerTracker.removedService(reference, service);
    }

}
