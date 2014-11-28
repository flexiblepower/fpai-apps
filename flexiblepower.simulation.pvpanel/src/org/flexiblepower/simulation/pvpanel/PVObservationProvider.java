package org.flexiblepower.simulation.pvpanel;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ext.AbstractObservationProvider;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.flexiblepower.ral.drivers.uncontrolled.PowerState;
import org.flexiblepower.time.TimeService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class PVObservationProvider extends AbstractObservationProvider<PowerState> {
    private final ServiceRegistration<?> observationProviderRegistration;
    private final TimeService timeService;

    public PVObservationProvider(BundleContext context, String observationOf, TimeService timeService) {
        observationProviderRegistration = new ObservationProviderRegistrationHelper(this).observationOf(observationOf)
                                                                                         .observedBy(PVSimulation.class.getName())
                                                                                         .observationType(PowerState.class)
                                                                                         .register();
        this.timeService = timeService;
    }

    public void close() {
        observationProviderRegistration.unregister();
    }

    public void publish(PowerState powerState) {
        publish(Observation.create(timeService.getTime(), powerState));
    }
}
