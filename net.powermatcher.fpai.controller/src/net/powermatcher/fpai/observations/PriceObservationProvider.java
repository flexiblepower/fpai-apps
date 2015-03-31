package net.powermatcher.fpai.observations;

import java.util.Date;

import net.powermatcher.api.monitoring.events.OutgoingPriceUpdateEvent;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ext.AbstractObservationProvider;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class PriceObservationProvider extends AbstractObservationProvider<PriceObservationProvider.State> {

    public static class State {
        private final Date time;
        private final String agentId;
        private final String clusterId;
        private final int bidNumber;
        private final double price;

        public State(OutgoingPriceUpdateEvent event) {
            time = event.getTimestamp();
            agentId = event.getAgentId();
            clusterId = event.getClusterId();
            bidNumber = event.getPriceUpdate().getBidNumber();
            price = event.getPriceUpdate().getPrice().getPriceValue();
        }

        public Date getTime() {
            return time;
        }

        public String getAgentId() {
            return agentId;
        }

        public String getClusterId() {
            return clusterId;
        }

        public int getBidNumber() {
            return bidNumber;
        }

        public double getPrice() {
            return price;
        }
    }

    private final ServiceRegistration<?> serviceRegistration;

    public PriceObservationProvider(BundleContext context) {
        serviceRegistration = new ObservationProviderRegistrationHelper(this, context).observationOf("PowerMatcher")
                                                                                      .observationType(State.class)
                                                                                      .observedBy(getClass().getName())
                                                                                      .register();
    }

    public void close() {
        serviceRegistration.unregister();
    }

    public void publish(OutgoingPriceUpdateEvent event) {
        publish(Observation.create(event.getTimestamp(), new State(event)));
    }
}
