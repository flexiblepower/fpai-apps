package org.flexiblepower.driver.pv.sma.impl;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ext.AbstractObservationProvider;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.flexiblepower.ral.drivers.uncontrolled.PowerState;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class PVObservationProvider extends AbstractObservationProvider<PowerState> {
    private final ServiceRegistration<?> observationProviderRegistration;
    private final FlexiblePowerContext fpContext;

    public PVObservationProvider(BundleContext context, String observationOf, FlexiblePowerContext fpContext) {
        observationProviderRegistration = new ObservationProviderRegistrationHelper(this).observationOf(observationOf)
                                                                                         .observedBy(SmaResourceDriver.class.getName())
                                                                                         .observationType(PowerState.class)
                                                                                         .register();
        this.fpContext = fpContext;
    }

    public void close() {
        observationProviderRegistration.unregister();
    }

    public void publish(PowerState powerState) {
        publish(Observation.create(fpContext.currentTime(), powerState));
    }
}
