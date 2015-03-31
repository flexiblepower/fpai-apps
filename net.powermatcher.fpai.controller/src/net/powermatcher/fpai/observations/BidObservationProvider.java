package net.powermatcher.fpai.observations;

import java.util.Date;

import net.powermatcher.api.monitoring.events.OutgoingBidUpdateEvent;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ext.AbstractObservationProvider;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class BidObservationProvider extends AbstractObservationProvider<BidObservationProvider.State> {

    public static class State {
        private final Date time;
        private final String agentId;
        private final String clusterId;
        private final int bidNumber;
        private final double[] bid;

        public State(OutgoingBidUpdateEvent event) {
            time = event.getTimestamp();
            agentId = event.getAgentId();
            clusterId = event.getClusterId();
            bidNumber = event.getBidUpdate().getBidNumber();
            bid = event.getBidUpdate().getBid().toArrayBid().getDemand();
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

        public double[] getBid() {
            return bid;
        }
    }

    private final ServiceRegistration<?> serviceRegistration;

    public BidObservationProvider(BundleContext context) {
        serviceRegistration = new ObservationProviderRegistrationHelper(this, context).observationOf("PowerMatcher")
                                                                                      .observationType(State.class)
                                                                                      .observedBy(getClass().getName())
                                                                                      .register();
    }

    public void close() {
        serviceRegistration.unregister();
    }

    public void publish(OutgoingBidUpdateEvent event) {
        publish(Observation.create(event.getTimestamp(), new State(event)));
    }
}
