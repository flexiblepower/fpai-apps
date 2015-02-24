package net.powermatcher.fpai.controller;

public interface AgentMessageSender {

    void destroyAgent();

    void sendMessage(Object message);

}
