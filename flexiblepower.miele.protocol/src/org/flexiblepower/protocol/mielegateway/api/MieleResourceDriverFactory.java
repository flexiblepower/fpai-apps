package org.flexiblepower.protocol.mielegateway.api;

import java.util.HashMap;
import java.util.Map;

import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.flexiblepower.ral.ResourceControlParameters;
import org.flexiblepower.ral.ResourceDriver;
import org.flexiblepower.ral.ResourceState;
import org.osgi.framework.ServiceRegistration;

public abstract class MieleResourceDriverFactory<RS extends ResourceState, RCP extends ResourceControlParameters, D extends MieleResourceDriver<RS, RCP>> {
	private final Class<?> observationClass;

	private final Map<String, ServiceRegistration<?>> registrations;

	public MieleResourceDriverFactory(Class<?> observationClass) {
		this.observationClass = observationClass;
		registrations = new HashMap<String, ServiceRegistration<?>>();
	}

	public abstract boolean canHandleType(String type);

	public abstract D create(ActionPerformer actionPerformer);

	public final synchronized D create(String name,
			ActionPerformer actionPerformer) {
		if (!registrations.containsKey(name)) {
			D driver = create(actionPerformer);

			ObservationProviderRegistrationHelper helper = new ObservationProviderRegistrationHelper(
					driver);
			ServiceRegistration<?> registration = helper.observationOf(name)
					.observedBy(driver.getClass().getName())
					.observationType(observationClass)
					.setProperty("resourceId", name)
					.register(ResourceDriver.class);

			registrations.put(name, registration);
			return driver;
		} else {
			return null;
		}
	}

	final synchronized void destroy(String name) {
		ServiceRegistration<?> serviceRegistration = registrations.remove(name);
		if (serviceRegistration != null) {
			serviceRegistration.unregister();
		}
	}

	public final synchronized void close() {
		for (ServiceRegistration<?> sr : registrations.values()) {
			sr.unregister();
		}
		registrations.clear();
	}
}
