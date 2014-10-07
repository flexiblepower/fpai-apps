package org.flexiblepower.simulation.pvpanel.test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.measure.unit.SI;

import junit.framework.Assert;

import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.MessageHandler;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.ral.drivers.uncontrolled.PowerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Port(name = "driver", accepts = PowerState.class)
public class OtherEndPVPanelManager implements Endpoint {
    private static final Logger log = LoggerFactory.getLogger(OtherEndPVPanelManager.class);
    private final BlockingQueue<PowerState> messages = new LinkedBlockingQueue<PowerState>();

    @Override
    public MessageHandler onConnect(Connection connection) {
        return new MessageHandler() {
            @Override
            public void handleMessage(Object message) {
                Assert.assertTrue(PowerState.class.isAssignableFrom(message.getClass()));
                PowerState powerState = (PowerState) message;
                log.debug("Received {}", powerState);
                messages.add(powerState);
            }

            @Override
            public void disconnected() {
            }
        };
    }

    public PowerState getState() throws InterruptedException {
        PowerState state = messages.poll(5, TimeUnit.SECONDS);
        Assert.assertNotNull("Not received a state update", state);
        return state;
    }

    public void expectedState(double minExpectedProduction, double maxExpectedProduction) throws InterruptedException {
        double production = getState().getCurrentUsage().doubleValue(SI.WATT);
        log.debug("Expecting min:{}, max: {}, current state {}",
                  minExpectedProduction,
                  maxExpectedProduction,
                  production);
        Assert.assertTrue("Production (" + production
                          + ") lower than minium production ("
                          + minExpectedProduction
                          + ")", minExpectedProduction <= production);
        Assert.assertTrue("Production (" + production
                          + ") higher dan maximum production ("
                          + maxExpectedProduction
                          + ")", maxExpectedProduction >= production);

    }

    public void expectedRandomValues(double minExpectedProduction,
                                     double maxExpectedProduction) throws InterruptedException {
        Set<Double> productions = new HashSet<Double>();
        int numberOfTests = 10;
        for (int i = 0; i < numberOfTests; i++) {
            double production = getState().getCurrentUsage().doubleValue(SI.WATT);
            log.info("production received: {}", production);

            Assert.assertTrue("Production (" + production
                              + ") lower than minium production ("
                              + minExpectedProduction
                              + ")", minExpectedProduction <= production);
            Assert.assertTrue("Production (" + production
                              + ") higher dan maximum production ("
                              + maxExpectedProduction
                              + ")", maxExpectedProduction >= production);
            productions.add(production);
        }
        // we assert that at leest 90% of all production numbers are different..
        // (because there is a random factor in use)
        Assert.assertTrue(productions.size() >= (9 * numberOfTests) / 10);
    }
}
