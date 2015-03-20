package net.powermatcher.fpai.controller;

import java.lang.reflect.InvocationTargetException;
import java.util.Hashtable;

import net.powermatcher.api.AgentEndpoint;
import net.powermatcher.api.monitoring.ObservableAgent;
import net.powermatcher.fpai.agents.FpaiAgent;

import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.MessageHandler;
import org.flexiblepower.ral.messages.AllocationStatusUpdate;
import org.flexiblepower.ral.messages.ControlSpaceRegistration;
import org.flexiblepower.ral.messages.ControlSpaceRevoke;
import org.flexiblepower.ral.messages.ControlSpaceUpdate;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentMessageHandler implements MessageHandler, AgentMessageSender {
    private static final Logger logger = LoggerFactory.getLogger(AgentMessageHandler.class);

    private final BundleContext bundleContext;
    private final PowerMatcherController controller;
    private final Connection connection;
    private final String agentPrefix, desiredParentId;
    private final Class<? extends FpaiAgent> type;

    private volatile FpaiAgent agent;
    private volatile ServiceRegistration<?> registration;

    public AgentMessageHandler(BundleContext bundleContext,
                               PowerMatcherController powerMatcherController,
                               Connection connection,
                               String agentPrefix,
                               String desiredParentId,
                               Class<? extends FpaiAgent> type) {
        this.bundleContext = bundleContext;
        controller = powerMatcherController;
        this.connection = connection;
        this.agentPrefix = agentPrefix;
        this.desiredParentId = desiredParentId;
        this.type = type;

        agent = null;
        registration = null;
    }

    @Override
    public synchronized void handleMessage(Object message) {
        if (message == null) {
            logger.error("Received a null message");
        } else if (message instanceof ControlSpaceRegistration) {
            ControlSpaceRegistration registration = (ControlSpaceRegistration) message;
            createAgent(agentPrefix + registration.getResourceId());
            agent.handleControlSpaceRegistration(registration);
        } else if (agent != null) {
            if (message instanceof ControlSpaceUpdate) {
                agent.handleControlSpaceUpdate((ControlSpaceUpdate) message);
            } else if (message instanceof ControlSpaceRevoke) {
                agent.handleControlSpaceRevoke((ControlSpaceRevoke) message);
            } else if (message instanceof AllocationStatusUpdate) {
                agent.handleAllocationStatusUpdate((AllocationStatusUpdate) message);
            } else {
                logger.error("Received unknown type of message: " + message);
            }
        }
    }

    private void createAgent(String agentId) {
        if (agent == null) {
            try {
                agent = type.getConstructor(AgentMessageSender.class, String.class, String.class)
                            .newInstance(this, agentId, desiredParentId);

                Hashtable<String, Object> properties = new Hashtable<String, Object>();
                properties.put("agentId", agentId);
                properties.put("desiredParent", desiredParentId);

                registration = bundleContext.registerService(new String[] { AgentEndpoint.class.getName(),
                                                                           ObservableAgent.class.getName() },
                                                             agent,
                                                             properties);
            } catch (IllegalArgumentException e) {
                logger.error("Could not create new instance of " + type.getName() + ": " + e.getMessage(), e);
                destroyAgent();
            } catch (SecurityException e) {
                logger.error("Could not create new instance of " + type.getName() + ": " + e.getMessage(), e);
                destroyAgent();
            } catch (InstantiationException e) {
                logger.error("Could not create new instance of " + type.getName() + ": " + e.getMessage(), e);
                destroyAgent();
            } catch (IllegalAccessException e) {
                logger.error("Could not create new instance of " + type.getName() + ": " + e.getMessage(), e);
                destroyAgent();
            } catch (InvocationTargetException e) {
                logger.error("Could not create new instance of " + type.getName() + ": " + e.getMessage(), e);
                destroyAgent();
            } catch (NoSuchMethodException e) {
                logger.error("Could not create new instance of " + type.getName() + ": " + e.getMessage(), e);
                destroyAgent();
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see net.powermatcher.fpai.agents.AgentMessageSender#destroyAgent()
     */
    @Override
    public void destroyAgent() {
        if (registration != null) {
            registration.unregister();
            registration = null;
        }
        if (agent != null) {
            agent.deactivate();
            agent = null;
        }
    }

    @Override
    public void disconnected() {
        controller.removeHandler(this);
    }

    /*
     * (non-Javadoc)
     *
     * @see net.powermatcher.fpai.agents.AgentMessageSender#sendMessage(java.lang.Object)
     */
    @Override
    public void sendMessage(Object message) {
        connection.sendMessage(message);
    }
}
