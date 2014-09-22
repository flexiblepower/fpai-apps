package org.flexiblepower.simulation.battery.test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.flexiblepower.efi.buffer.BufferAllocation;
import org.flexiblepower.efi.buffer.BufferRegistration;
import org.flexiblepower.efi.buffer.BufferStateUpdate;
import org.flexiblepower.messaging.Cardinality;
import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.MessageHandler;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.rai.AllocationRevoke;
import org.flexiblepower.rai.AllocationStatusUpdate;
import org.flexiblepower.rai.ControlSpaceRevoke;
import org.flexiblepower.rai.ResourceMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Port(name = "Buffer",
      sends = { BufferAllocation.class, AllocationRevoke.class },
      accepts = { BufferRegistration.class,
                 BufferStateUpdate.class,
                 AllocationStatusUpdate.class,
                 ControlSpaceRevoke.class },
      cardinality = Cardinality.SINGLE)
public class OtherEndBatteryApp implements Endpoint {
    private static final Logger log = LoggerFactory.getLogger(OtherEndBatteryApp.class);
    private volatile Connection connection;

    public Connection getConnection() {
        return connection;
    }

    private final BlockingQueue<BufferRegistration> bufferRegistrations = new LinkedBlockingQueue<BufferRegistration>();
    private final BlockingQueue<BufferStateUpdate> bufferStateUpdates = new LinkedBlockingQueue<BufferStateUpdate>();
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

                if (BufferRegistration.class.isAssignableFrom(message.getClass())) {
                    BufferRegistration resourceMessage = (BufferRegistration) message;
                    log.debug("Received BufferRegistration");
                    bufferRegistrations.add(resourceMessage);
                } else if (BufferStateUpdate.class.isAssignableFrom(message.getClass())) {
                    BufferStateUpdate resourceMessage = (BufferStateUpdate) message;
                    log.debug("Received BufferStateUpdate");
                    bufferStateUpdates.add(resourceMessage);
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
                OtherEndBatteryApp.this.connection = null;
            }
        };
    }

    public BufferRegistration getBufferRegistration() throws InterruptedException {
        BufferRegistration state = bufferRegistrations.poll(5, TimeUnit.SECONDS);
        Assert.assertNotNull(state);
        return state;
    }

    public BufferStateUpdate getBufferStateUpdate() throws InterruptedException {
        BufferStateUpdate state = bufferStateUpdates.poll(5, TimeUnit.SECONDS);
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
