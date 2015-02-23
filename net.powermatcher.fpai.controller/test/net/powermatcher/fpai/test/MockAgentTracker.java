package net.powermatcher.fpai.test;

import net.powermatcher.fpai.agents.FpaiAgent;
import net.powermatcher.fpai.controller.AgentTracker;

public class MockAgentTracker implements AgentTracker {

    private boolean registered = false;
    private boolean unregistered = false;

    public boolean hasRegistered() {
        return registered;
    }

    public boolean hasUnregistered() {
        return unregistered;
    }

    @Override
    public void registerAgent(FpaiAgent agent) {
        registered = true;
    }

    @Override
    public void unregisterAgent(FpaiAgent agent) {
        unregistered = true;
    }

    public void reset() {
        registered = false;
        unregistered = false;
    }

}
