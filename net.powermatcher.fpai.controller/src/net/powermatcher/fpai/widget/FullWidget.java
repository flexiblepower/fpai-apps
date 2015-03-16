package net.powermatcher.fpai.widget;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import net.powermatcher.api.data.MarketBasis;
import net.powermatcher.api.data.PriceStep;
import net.powermatcher.api.messages.BidUpdate;
import net.powermatcher.api.messages.PriceUpdate;
import net.powermatcher.api.monitoring.AgentObserver;
import net.powermatcher.api.monitoring.ObservableAgent;
import net.powermatcher.api.monitoring.events.AgentEvent;
import net.powermatcher.api.monitoring.events.IncomingPriceUpdateEvent;
import net.powermatcher.api.monitoring.events.OutgoingBidUpdateEvent;

import org.flexiblepower.ui.Widget;

import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Meta;

@Component(properties = { "widget.type=full", "widget.name=pmfullwidget" },
           provide = Widget.class,
           designate = FullWidget.Config.class)
public class FullWidget implements Widget, AgentObserver {
    private final Map<String, AgentInfo> bids = new ConcurrentHashMap<String, AgentInfo>();

    public interface Config {
        @Meta.AD(deflt = "", description = "A filter for only showing certain type of observable agents")
        String agent_target();
    }

    @Reference(dynamic = true, multiple = true)
    public void addAgent(ObservableAgent agent) {
        bids.put(agent.getAgentId(), new AgentInfo(agent.getAgentId()));
        agent.addObserver(this);
    }

    public void removeAgent(ObservableAgent agent) {
        agent.removeObserver(this);
        bids.remove(agent.getAgentId());
    }

    @Override
    public void handleAgentEvent(AgentEvent event) {
        AgentInfo info = bids.get(event.getAgentId());
        if (info != null) {
            if (event instanceof OutgoingBidUpdateEvent) {
                info.setBid(((OutgoingBidUpdateEvent) event).getBidUpdate());
            } else if (event instanceof IncomingPriceUpdateEvent) {
                info.setPrice(((IncomingPriceUpdateEvent) event).getPriceUpdate());
            }
        }
    }

    @Override
    public String getTitle(Locale locale) {
        return "PowerMatcher overview";
    }

    public Map<String, AgentInfo> update() {
        Map<String, AgentInfo> copy = new TreeMap<String, FullWidget.AgentInfo>(bids);
        for (Iterator<AgentInfo> it = copy.values().iterator(); it.hasNext();) {
            AgentInfo agentInfo = it.next();
            if (agentInfo.priceBidNumber == 0 || agentInfo.coordinates.length == 0) {
                it.remove();
            }
        }
        return copy;
    }

    public static class AgentInfo {
        public final String agentId;
        public volatile double[][] coordinates;
        public volatile int bidNumber;
        public volatile double price;
        public volatile int priceBidNumber;

        public AgentInfo(String agentId) {
            this.agentId = agentId;
            coordinates = new double[0][];
            price = 0;
        }

        public void setBid(BidUpdate bidUpdate) {
            double[] demand = bidUpdate.getBid().toArrayBid().getDemand();

            double[][] coordinates = new double[demand.length][];
            MarketBasis mb = bidUpdate.getBid().getMarketBasis();
            for (int i = 0; i < demand.length; i++) {
                coordinates[i] = new double[] { new PriceStep(mb, i).toPrice().getPriceValue(), demand[i] };
            }

            this.coordinates = coordinates;
            bidNumber = bidUpdate.getBidNumber();
        }

        public void setPrice(PriceUpdate priceUpdate) {
            price = priceUpdate.getPrice().getPriceValue();
            priceBidNumber = priceUpdate.getBidNumber();
        }
    }
}
