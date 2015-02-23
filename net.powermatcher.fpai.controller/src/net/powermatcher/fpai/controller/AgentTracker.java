package net.powermatcher.fpai.controller;

import net.powermatcher.fpai.agents.FpaiAgent;

public interface AgentTracker {

    void registerAgent(FpaiAgent agent);

    void unregisterAgent(FpaiAgent agent);

}
