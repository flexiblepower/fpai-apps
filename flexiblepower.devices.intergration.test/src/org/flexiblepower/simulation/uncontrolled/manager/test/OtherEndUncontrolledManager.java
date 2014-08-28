package org.flexiblepower.simulation.uncontrolled.manager.test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.measure.Measurable;
import javax.measure.quantity.Duration;
import javax.measure.unit.SI;

import junit.framework.Assert;

import org.flexiblepower.efi.uncontrolled.UncontrolledAllocation;
import org.flexiblepower.efi.uncontrolled.UncontrolledMeasurement;
import org.flexiblepower.efi.uncontrolled.UncontrolledRegistration;
import org.flexiblepower.efi.uncontrolled.UncontrolledUpdate;
import org.flexiblepower.messaging.Cardinality;
import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.MessageHandler;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.rai.comm.AllocationRevoke;
import org.flexiblepower.rai.comm.AllocationStatusUpdate;
import org.flexiblepower.rai.comm.ControlSpaceRevoke;
import org.flexiblepower.rai.comm.ResourceMessage;
import org.flexiblepower.rai.values.Commodity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Port(name = "uncontrolled",
      accepts = { UncontrolledRegistration.class,
                 UncontrolledUpdate.class,
                 AllocationStatusUpdate.class,
                 ControlSpaceRevoke.class },
      sends = { UncontrolledAllocation.class, AllocationRevoke.class },
      cardinality = Cardinality.SINGLE)
public class OtherEndUncontrolledManager implements Endpoint {
    private static final Logger log = LoggerFactory.getLogger(OtherEndUncontrolledManager.class);
    private volatile Connection connection;
    private final BlockingQueue<UncontrolledRegistration> uncontrolledRegistrations = new LinkedBlockingQueue<UncontrolledRegistration>();
    private final BlockingQueue<UncontrolledUpdate> uncontrolledUpdates = new LinkedBlockingQueue<UncontrolledUpdate>();
    private final BlockingQueue<AllocationStatusUpdate> allocationStatusUpdates = new LinkedBlockingQueue<AllocationStatusUpdate>();
    private final BlockingQueue<ControlSpaceRevoke> controlSpaceRevokes = new LinkedBlockingQueue<ControlSpaceRevoke>();

    @Override
    public MessageHandler onConnect(Connection connection) {
        this.connection = connection;
        return new MessageHandler() {
            @Override
            public void handleMessage(Object message) {
                log.info("message received");
                Assert.assertTrue(ResourceMessage.class.isAssignableFrom(message.getClass()));

                if (UncontrolledRegistration.class.isAssignableFrom(message.getClass())) {
                    UncontrolledRegistration resourceMessage = (UncontrolledRegistration) message;
                    log.debug("Received UncontrolledRegistration");
                    uncontrolledRegistrations.add(resourceMessage);

                } else if (UncontrolledUpdate.class.isAssignableFrom(message.getClass())) {
                    UncontrolledUpdate resourceMessage = (UncontrolledUpdate) message;
                    log.debug("Received UncontrolledUpdate");
                    uncontrolledUpdates.add(resourceMessage);

                } else if (AllocationStatusUpdate.class.isAssignableFrom(message.getClass())) {
                    AllocationStatusUpdate resourceMessage = (AllocationStatusUpdate) message;
                    log.debug("Received AllocationStatusUpdate");
                    allocationStatusUpdates.add(resourceMessage);

                } else if (ControlSpaceRevoke.class.isAssignableFrom(message.getClass())) {
                    ControlSpaceRevoke resourceMessage = (ControlSpaceRevoke) message;
                    log.debug("Received ControlSpaceRevoke");
                    controlSpaceRevokes.add(resourceMessage);

                } else {
                    throw new AssertionError();
                }

            }

            @Override
            public void disconnected() {
                OtherEndUncontrolledManager.this.connection = null;
            }
        };
    }

    public UncontrolledRegistration getUncontrolledRegistration() throws InterruptedException {
        UncontrolledRegistration state = uncontrolledRegistrations.poll(5, TimeUnit.SECONDS);
        Assert.assertNotNull(state);
        return state;
    }

    public UncontrolledUpdate getUncontrolledUpdate() throws InterruptedException {
        UncontrolledUpdate state = uncontrolledUpdates.poll(5, TimeUnit.SECONDS);
        Assert.assertNotNull(state);
        return state;
    }

    public AllocationStatusUpdate getAllocationStatusUpdate() throws InterruptedException {
        AllocationStatusUpdate state = allocationStatusUpdates.poll(5, TimeUnit.SECONDS);
        Assert.assertNotNull(state);
        return state;
    }

    public ControlSpaceRevoke getControlSpaceRevoke() throws InterruptedException {
        ControlSpaceRevoke state = controlSpaceRevokes.poll(5, TimeUnit.SECONDS);
        Assert.assertNotNull(state);
        return state;
    }

    public void expectedRegistration(Measurable<Duration> delay) throws InterruptedException {
        UncontrolledRegistration registration = getUncontrolledRegistration();
        Assert.assertNotNull(registration);
        Measurable<Duration> registredDelay = registration.getAllocationDelay();
        Assert.assertEquals(delay.doubleValue(SI.SECOND), registredDelay.doubleValue(SI.SECOND));

    }

    public void expectedState(double minExpectedProduction, double maxExpectedProduction) throws InterruptedException {
        double production = ((UncontrolledMeasurement) getUncontrolledUpdate()).getMeasurements()
                                                                               .get(Commodity.ELECTRICITY)
                                                                               .doubleValue(SI.WATT);
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

    public void
            expectedRandomValues(double minExpectedProduction, double maxExpectedProduction) throws InterruptedException {
        Set<Double> productions = new HashSet<Double>();
        int numberOfTests = 10;
        for (int i = 0; i < numberOfTests; i++) {
            double production = ((UncontrolledMeasurement) getUncontrolledUpdate()).getMeasurements()
                                                                                   .get(Commodity.ELECTRICITY)
                                                                                   .doubleValue(SI.WATT);
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
        // we assert that at leest 90% of all production numbers are different.. (because there is a random factor in
        // use)
        Assert.assertTrue(productions.size() >= 0.9 * numberOfTests);
    }

}
