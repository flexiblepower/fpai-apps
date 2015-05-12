package net.powermatcher.fpai.widget;

import java.text.DateFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.powermatcher.api.monitoring.AgentObserver;
import net.powermatcher.api.monitoring.ObservableAgent;
import net.powermatcher.api.monitoring.events.AgentEvent;
import net.powermatcher.api.monitoring.events.OutgoingBidUpdateEvent;
import net.powermatcher.api.monitoring.events.OutgoingPriceUpdateEvent;

import org.flexiblepower.ui.Widget;

import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Meta;

@Component(provide = Widget.class, designateFactory = SmallWidget.Config.class)
public class SmallWidget implements AgentObserver, Widget {
    private static final DateFormat DF = DateFormat.getTimeInstance(DateFormat.LONG);

    private String price = "no price yet";
    private String priceTime = "";
    private final Map<String, String> demands = new ConcurrentHashMap<String, String>();

    public interface Config {
        @Meta.AD(deflt = "", description = "A filter for only showing certain type of observable agents")
        String agent_target();
    }

    @Reference(dynamic = true, multiple = true)
    public void addAgent(ObservableAgent agent) {
        agent.addObserver(this);
    }

    public void removeAgent(ObservableAgent agent) {
        agent.removeObserver(this);
        demands.remove(agent.getAgentId());
    }

    @Override
    public String getTitle(Locale locale) {
        return "PowerMatcher";
    }

    @Override
    public void handleAgentEvent(AgentEvent event) {
        if (event instanceof OutgoingPriceUpdateEvent) {
            OutgoingPriceUpdateEvent e = (OutgoingPriceUpdateEvent) event;
            price = String.format("%1.2f", e.getPriceUpdate().getPrice().getPriceValue());
            priceTime = DF.format(e.getTimestamp());
        } else if (event instanceof OutgoingBidUpdateEvent) {
            OutgoingBidUpdateEvent e = (OutgoingBidUpdateEvent) event;
            demands.put(e.getAgentId(), getDemands(e));
        }
    }

    public Update update(Locale locale) {
        return new Update(price, priceTime, demands);
    }

    private String getDemands(OutgoingBidUpdateEvent bid) {
        double[] demand = bid.getBidUpdate().getBid().toArrayBid().getDemand();
        double first = demand[0] / 1000;
        double last = demand[demand.length - 1] / 1000;

        if (Math.abs(first - last) < .0001) {
            return String.format("%.2f kW", first);
        } else {
            return String.format("%.2f - %.2f kW", last, first);
        }
    }

    public static class Update {

        private final String price;
        private final String timestamp;
        private final Map<String, String> demands;

        public Update(String price, String priceTime, Map<String, String> demands) {
            this.price = price;
            timestamp = priceTime;
            this.demands = demands;
        }

        public String getPrice() {
            return price;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public Map<String, String> getDemands() {
            return demands;
        }
    }
}
