package net.powermatcher.fpai.observations;

import java.util.Map;

import net.powermatcher.api.monitoring.AgentObserver;
import net.powermatcher.api.monitoring.ObservableAgent;
import net.powermatcher.api.monitoring.events.AgentEvent;
import net.powermatcher.api.monitoring.events.AggregatedBidEvent;
import net.powermatcher.api.monitoring.events.OutgoingBidUpdateEvent;
import net.powermatcher.api.monitoring.events.OutgoingPriceUpdateEvent;

import org.osgi.framework.BundleContext;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = PowerMatcherObserver.Config.class)
public class PowerMatcherObserver implements AgentObserver {
    public interface Config {
        @Meta.AD(description = "Whether outgoing price events should be published", deflt = "true")
        boolean publishPriceEvents();

        @Meta.AD(description = "Whether outgoing bid events should be published", deflt = "true")
        boolean publishBidEvents();

        @Meta.AD(description = "Whether aggregation events should be published", deflt = "true")
        boolean publishAggregationEvents();

        @Meta.AD(name = "agent.target", deflt = "")
        String agentTarget();
    }

    @Reference(dynamic = true, multiple = true, optional = true)
    public void addAgent(ObservableAgent agent) {
        agent.addObserver(this);
    }

    public void removeAgent(ObservableAgent agent) {
        agent.removeObserver(this);
    }

    private PriceObservationProvider pricePublisher = null;
    private BidObservationProvider bidPublisher = null;
    private AggregationObservationProvider aggregationPublisher;

    @Activate
    public void activate(Map<String, Object> properties, BundleContext context) {
        Config config = Configurable.createConfigurable(Config.class, properties);
        if (config.publishPriceEvents()) {
            pricePublisher = new PriceObservationProvider(context);
        }
        if (config.publishBidEvents()) {
            bidPublisher = new BidObservationProvider(context);
        }
        if (config.publishAggregationEvents()) {
            aggregationPublisher = new AggregationObservationProvider(context);
        }
    }

    @Deactivate
    public void deactivate() {
        if (pricePublisher != null) {
            pricePublisher.close();
            pricePublisher = null;
        }
        if (bidPublisher != null) {
            bidPublisher.close();
            bidPublisher = null;
        }
        if (aggregationPublisher != null) {
            aggregationPublisher.close();
            aggregationPublisher = null;
        }
    }

    @Override
    public void handleAgentEvent(AgentEvent event) {
        if (pricePublisher != null && event instanceof OutgoingPriceUpdateEvent) {
            pricePublisher.publish((OutgoingPriceUpdateEvent) event);
        } else if (bidPublisher != null && event instanceof OutgoingBidUpdateEvent) {
            bidPublisher.publish((OutgoingBidUpdateEvent) event);
        } else if (aggregationPublisher != null && event instanceof AggregatedBidEvent) {
            aggregationPublisher.publish((AggregatedBidEvent) event);
        }
    }
}
