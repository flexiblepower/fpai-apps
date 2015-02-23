package net.powermatcher.fpai.controller;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.powermatcher.api.AgentEndpoint;
import net.powermatcher.fpai.agents.BufferAgent;
import net.powermatcher.fpai.agents.FpaiAgent;
import net.powermatcher.fpai.agents.TimeshifterAgent;
import net.powermatcher.fpai.agents.UnconstrainedAgent;
import net.powermatcher.fpai.agents.UncontrolledAgent;
import net.powermatcher.fpai.controller.PowerMatcherController.Config;

import org.flexiblepower.efi.EfiControllerManager;
import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.MessageHandler;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(immediate = true, designateFactory = Config.class, provide = { Endpoint.class })
public class PowerMatcherController implements EfiControllerManager, AgentTracker {

    public interface Config {
        @Meta.AD(deflt = "auctioneer", required = false)
        String desiredParent();

        @Meta.AD(deflt = "fpai-agent-")
        String agentIdPrefix();
    }

    private BundleContext bundleContext;

    private final Map<FpaiAgent, ServiceRegistration<AgentEndpoint>> agents = new ConcurrentHashMap<FpaiAgent, ServiceRegistration<AgentEndpoint>>();

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
        // remove all agents
        for (FpaiAgent agent : agents.keySet().toArray(new FpaiAgent[agents.size()])) {
            unregisterAgent(agent);
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public MessageHandler onConnect(Connection connection) {
        FpaiAgent newAgent;
        String agentId = agentIdPrefix + this.agentId.getAndIncrement();

        if ("buffer".equals(connection.getPort().name())) {
            newAgent = new BufferAgent(connection, this, agentId, desiredParent);
        } else if ("timeshifter".equals(connection.getPort().name())) {
            newAgent = new TimeshifterAgent(connection, this, agentId, desiredParent);
        } else if ("unconstrained".equals(connection.getPort().name())) {
            newAgent = new UnconstrainedAgent(connection, this, agentId, desiredParent);
        } else if ("uncontrolled".equals(connection.getPort().name())) {
            newAgent = new UncontrolledAgent(connection, this, agentId, desiredParent);
        } else {
            // Wut?
            throw new IllegalArgumentException("Unknown type of connection");
        }
        registerAgent(newAgent);
        return newAgent;
    }

    public Set<FpaiAgent> getAgentList() {
        return agents.keySet();
    }

    @Override
    public void registerAgent(FpaiAgent agent) {
        ServiceRegistration<AgentEndpoint> serviceRegistration = bundleContext.registerService(AgentEndpoint.class,
                                                                                               agent,
                                                                                               null);
        agents.put(agent, serviceRegistration);
    }

    @Override
    public void unregisterAgent(FpaiAgent agent) {
        ServiceRegistration<AgentEndpoint> serviceRegistration = agents.remove(agent);
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
        }
    }
}
