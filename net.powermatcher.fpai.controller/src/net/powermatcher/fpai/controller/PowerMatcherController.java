package net.powermatcher.fpai.controller;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import net.powermatcher.fpai.agents.BufferAgent;
import net.powermatcher.fpai.agents.TimeshifterAgent;
import net.powermatcher.fpai.agents.UnconstrainedAgent;
import net.powermatcher.fpai.agents.UncontrolledAgent;
import net.powermatcher.fpai.controller.PowerMatcherController.Config;

import org.flexiblepower.efi.EfiControllerManager;
import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.MessageHandler;
import org.osgi.framework.BundleContext;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(immediate = true, designateFactory = Config.class, provide = { Endpoint.class })
public class PowerMatcherController implements EfiControllerManager {

    public interface Config {
        @Meta.AD(deflt = "auctioneer", required = false)
        String desiredParent();

        @Meta.AD(deflt = "fpai-agent-")
        String agentIdPrefix();
    }

    private BundleContext bundleContext;

    private final Set<AgentMessageHandler> activeHandlers = new HashSet<AgentMessageHandler>();

    private final AtomicInteger agentId = new AtomicInteger(1);

    private String agentIdPrefix;

    private String desiredParent;

    @Activate
    public void activate(BundleContext context, Map<String, Object> properties) throws Exception {
        bundleContext = context;
        Config config = Configurable.createConfigurable(Config.class, properties);
        agentIdPrefix = config.agentIdPrefix();
        desiredParent = config.desiredParent();
    }

    @Deactivate
    public void deactivate() {
        synchronized (activeHandlers) {
            for (AgentMessageSender handler : activeHandlers.toArray(new AgentMessageHandler[activeHandlers.size()])) {
                removeHandler(handler);
            }
        }
    }

    @Override
    public MessageHandler onConnect(Connection connection) {
        AgentMessageHandler newHandler;
        String agentId = agentIdPrefix + this.agentId.getAndIncrement() + "-";

        if ("buffer".equals(connection.getPort().name())) {
            newHandler = new AgentMessageHandler(bundleContext,
                                                 this,
                                                 connection,
                                                 agentId,
                                                 desiredParent,
                                                 BufferAgent.class);
        } else if ("timeshifter".equals(connection.getPort().name())) {
            newHandler = new AgentMessageHandler(bundleContext,
                                                 this,
                                                 connection,
                                                 agentId,
                                                 desiredParent,
                                                 TimeshifterAgent.class);
        } else if ("unconstrained".equals(connection.getPort().name())) {
            newHandler = new AgentMessageHandler(bundleContext,
                                                 this,
                                                 connection,
                                                 agentId,
                                                 desiredParent,
                                                 UnconstrainedAgent.class);
        } else if ("uncontrolled".equals(connection.getPort().name())) {
            newHandler = new AgentMessageHandler(bundleContext,
                                                 this,
                                                 connection,
                                                 agentId,
                                                 desiredParent,
                                                 UncontrolledAgent.class);
        } else {
            // Wut?
            throw new IllegalArgumentException("Unknown type of connection");
        }

        synchronized (activeHandlers) {
            activeHandlers.add(newHandler);
        }
        return newHandler;
    }

    public void removeHandler(AgentMessageSender handler) {
        synchronized (activeHandlers) {
            activeHandlers.remove(handler);
            handler.destroyAgent();
        }
    }
}
