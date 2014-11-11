package nl.tno.fpai.monitoring.ui.web.client;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

public class Activator implements BundleActivator {
	private HttpService http;

	@Override
	public void start(final BundleContext context) throws Exception {
		ServiceReference<HttpService> httpRef = context
				.getServiceReference(HttpService.class);
		if (httpRef != null) {
			http = context.getService(httpRef);
			registerResources(http);
		}

		ServiceListener listener = new ServiceListener() {
			@Override
			public void serviceChanged(ServiceEvent event) {
				switch (event.getType()) {
				case ServiceEvent.REGISTERED:
					ServiceReference<?> httpRef = event.getServiceReference();
					http = (HttpService) context.getService(httpRef);
					registerResources(http);
					break;
				}
			}
		};
		
		context.addServiceListener(listener, String.format("(%s=%s)",
				Constants.OBJECTCLASS, HttpService.class.getName()));
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		http.unregister("/ui");
	}

	private void registerResources(HttpService http) {
		HttpContext httpContext = http.createDefaultHttpContext();

		try {
			http.registerResources("/ui", "/web", httpContext);
		} catch (NamespaceException e) {
			e.printStackTrace();
		}
	}
}
