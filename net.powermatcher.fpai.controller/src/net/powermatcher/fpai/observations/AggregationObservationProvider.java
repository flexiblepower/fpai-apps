package net.powermatcher.fpai.observations;

import java.util.Date;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ext.AbstractObservationProvider;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import net.powermatcher.api.monitoring.events.AggregatedBidEvent;

public class AggregationObservationProvider extends AbstractObservationProvider<AggregationObservationProvider.State> {

    public static class State {
        private final Date time;
        private final String agentId;
        private final String clusterId;
        private final double[] bid;

        public State(AggregatedBidEvent event) {
            time = event.getTimestamp();
            agentId = event.getAgentId();
            clusterId = event.getClusterId();
            bid = event.getAggregatedBid().getDemand();
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

        public double[] getBid() {
            return bid;
        }
    }

    private final ServiceRegistration<?> serviceRegistration;

    public AggregationObservationProvider(BundleContext context) {
        serviceRegistration = new ObservationProviderRegistrationHelper(this, context).observationOf("PowerMatcher")
                                                                                      .observationType(State.class)
                                                                                      .observedBy(getClass().getName())
                                                                                      .register();
    }

    public void close() {
        serviceRegistration.unregister();
    }

    public void publish(AggregatedBidEvent event) {
        publish(Observation.create(event.getTimestamp(), new State(event)));
    }
}
