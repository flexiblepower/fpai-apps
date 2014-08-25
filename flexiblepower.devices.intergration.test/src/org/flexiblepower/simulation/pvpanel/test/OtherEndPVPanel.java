package org.flexiblepower.simulation.pvpanel.test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.measure.unit.SI;

import junit.framework.Assert;

import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.MessageHandler;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.ral.ResourceControlParameters;
import org.flexiblepower.ral.drivers.uncontrolled.PowerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Port(name = "driver", accepts = PowerState.class, sends = ResourceControlParameters.class)
public class OtherEndPVPanel implements Endpoint {
    private static final Logger log = LoggerFactory.getLogger(OtherEndPVPanel.class);
    private volatile Connection connection;
    private final BlockingQueue<PowerState> messages = new LinkedBlockingQueue<PowerState>();

    @Override
    public MessageHandler onConnect(Connection connection) {
        this.connection = connection;
        return new MessageHandler() {
            @Override
            public void handleMessage(Object message) {
                log.info("message received");
                Assert.assertTrue(PowerState.class.isAssignableFrom(message.getClass()));
                PowerState powerState = (PowerState) message;
                log.debug("Received pvPanel state");
                messages.add(powerState);
            }

            @Override
            public void disconnected() {
                OtherEndPVPanel.this.connection = null;
            }
        };
    }

    public PowerState getState() throws InterruptedException {
        log.info("messageQueue in PVpanelsim OtherEnd is {}", messages.size());
        PowerState state = messages.poll(5, TimeUnit.SECONDS);
        log.info("Message received: {}", state.getCurrentUsage());
        Assert.assertNotNull(state);
        return state;
    }

    public void expectedState(double minExpectedProduction, double maxExpectedProduction) throws InterruptedException {
        double production = getState().getCurrentUsage().doubleValue(SI.WATT);
        log.debug("Expecting min:{}, max: {}, current state {}",
                  minExpectedProduction,
                  maxExpectedProduction,
                  production);
        Assert.assertTrue(minExpectedProduction <= production);
        Assert.assertTrue(maxExpectedProduction >= production);

        lastProduction = production;
    }

    private double lastProduction = -1;

    public void expectedDifferentState() throws InterruptedException {
        double production = getState().getCurrentUsage().doubleValue(SI.WATT);
        Assert.assertFalse(production != lastProduction); // maybe to tight? it is possible that the production is equal
                                                          // over 2 measurements...
        lastProduction = production;

    }

    public void expectSameState() throws InterruptedException {
        double production = getState().getCurrentUsage().doubleValue(SI.WATT);
        Assert.assertEquals(lastProduction, production);
        lastProduction = production;
    }

}
