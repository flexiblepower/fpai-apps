package org.flexiblepower.simulation.dishwasher.test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.flexiblepower.efi.timeshifter.TimeShifterAllocation;
import org.flexiblepower.efi.timeshifter.TimeShifterRegistration;
import org.flexiblepower.efi.timeshifter.TimeShifterUpdate;
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

@Port(name = "timeshifter",
      sends = { TimeShifterAllocation.class,
               AllocationRevoke.class },
      accepts = { TimeShifterRegistration.class,
                 TimeShifterUpdate.class,
                 AllocationStatusUpdate.class,
                 ControlSpaceRevoke.class })
public class OtherEndEnergyApp implements Endpoint {
    private static final Logger log = LoggerFactory.getLogger(OtherEndEnergyApp.class);
    private volatile Connection connection;

    public Connection getConnection() {
        return connection;
    }

    private final BlockingQueue<TimeShifterRegistration> timeShifterRegistrations = new LinkedBlockingQueue<TimeShifterRegistration>();
    private final BlockingQueue<TimeShifterUpdate> timeShifterUpdates = new LinkedBlockingQueue<TimeShifterUpdate>();
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

                if (TimeShifterRegistration.class.isAssignableFrom(message.getClass())) {
                    TimeShifterRegistration resourceMessage = (TimeShifterRegistration) message;
                    log.debug("Received TimeShifterRegistration");
                    timeShifterRegistrations.add(resourceMessage);
                } else if (TimeShifterUpdate.class.isAssignableFrom(message.getClass())) {
                    TimeShifterUpdate resourceMessage = (TimeShifterUpdate) message;
                    log.debug("Received TimeShifterUpdate");
                    timeShifterUpdates.add(resourceMessage);
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
                OtherEndEnergyApp.this.connection = null;
            }
        };
    }

    public TimeShifterRegistration getTimeshifterRegistration() throws InterruptedException {
        TimeShifterRegistration state = timeShifterRegistrations.poll(5, TimeUnit.SECONDS);
        Assert.assertNotNull(state);
        return state;
    }

    public TimeShifterUpdate getTimeshifterUpdate() throws InterruptedException {
        TimeShifterUpdate state = timeShifterUpdates.poll(5, TimeUnit.SECONDS);
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
