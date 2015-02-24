package net.powermatcher.fpai.test;

import net.powermatcher.fpai.agents.FpaiAgent;
import net.powermatcher.fpai.controller.AgentMessageSender;

import org.flexiblepower.ral.messages.AllocationStatusUpdate;
import org.flexiblepower.ral.messages.ControlSpaceRegistration;
import org.flexiblepower.ral.messages.ControlSpaceRevoke;
import org.flexiblepower.ral.messages.ControlSpaceUpdate;

public class MockAgentSender<T extends FpaiAgent> implements AgentMessageSender {
    private boolean destroyed;
    private Object lastMessage;
    private final T agent;

    public static <T extends FpaiAgent> MockAgentSender<T> create(Class<T> clazz) throws Exception {
        return new MockAgentSender<T>(clazz);
    }

    public MockAgentSender(Class<T> clazz) throws Exception {
        destroyed = false;
        lastMessage = null;

        agent = clazz.getConstructor(AgentMessageSender.class).newInstance(this);
    }

    public T getAgent() {
        return agent;
    }

    @Override
    public void destroyAgent() {
        destroyed = true;
    }

    @Override
    public void sendMessage(Object message) {
        lastMessage = message;
    }

    public Object getLastMessage() {
        return lastMessage;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public void handleMessage(Object message) {
        if (message == null) {
        } else if (message instanceof ControlSpaceRegistration) {
            agent.handleControlSpaceRegistration((ControlSpaceRegistration) message);
        } else if (message instanceof ControlSpaceUpdate) {
            agent.handleControlSpaceUpdate((ControlSpaceUpdate) message);
        } else if (message instanceof ControlSpaceRevoke) {
            agent.handleControlSpaceRevoke((ControlSpaceRevoke) message);
        } else if (message instanceof AllocationStatusUpdate) {
            agent.handleAllocationStatusUpdate((AllocationStatusUpdate) message);
        }
    }
}
