package org.flexiblepower.simulation.pvpanel.test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.flexiblepower.efi.uncontrolled.UncontrolledAllocation;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Port(name = "Uncontrolled",
      sends = { UncontrolledAllocation.class, AllocationRevoke.class },
      accepts = { UncontrolledRegistration.class,
                 UncontrolledUpdate.class,
                 AllocationStatusUpdate.class,
                 ControlSpaceRevoke.class },
      cardinality = Cardinality.SINGLE)
public class OtherEndPVPanelApp implements Endpoint {
    private static final Logger log = LoggerFactory.getLogger(OtherEndPVPanelApp.class);
    private volatile Connection connection;

    public Connection getConnection() {
        return connection;
    }

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
                log.info("Received message");
                Assert.assertTrue(ResourceMessage.class.isAssignableFrom(message.getClass()));

                if (UncontrolledRegistration.class.isAssignableFrom(message.getClass())) {
                    UncontrolledRegistration resourceMessage = (UncontrolledRegistration) message;
                    log.debug("Received TimeShifterRegistration");
                    uncontrolledRegistrations.add(resourceMessage);
                } else if (UncontrolledUpdate.class.isAssignableFrom(message.getClass())) {
                    UncontrolledUpdate resourceMessage = (UncontrolledUpdate) message;
                    log.debug("Received TimeShifterUpdate");
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
                OtherEndPVPanelApp.this.connection = null;
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
}
