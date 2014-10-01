package org.flexiblepower.simulation.battery.test;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.measure.quantity.Energy;

import junit.framework.Assert;

import org.flexiblepower.efi.buffer.ActuatorAllocation;
import org.flexiblepower.efi.buffer.ActuatorBehaviour;
import org.flexiblepower.efi.buffer.BufferAllocation;
import org.flexiblepower.efi.buffer.BufferRegistration;
import org.flexiblepower.efi.buffer.BufferStateUpdate;
import org.flexiblepower.efi.buffer.BufferSystemDescription;
import org.flexiblepower.efi.buffer.RunningModeBehaviour;
import org.flexiblepower.efi.util.FillLevelFunction;
import org.flexiblepower.efi.util.RunningMode;
import org.flexiblepower.messaging.Cardinality;
import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.MessageHandler;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.rai.AllocationRevoke;
import org.flexiblepower.rai.AllocationStatusUpdate;
import org.flexiblepower.rai.ControlSpaceRevoke;
import org.flexiblepower.rai.ResourceMessage;
import org.flexiblepower.ral.drivers.battery.BatteryMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Port(name = "Buffer",
      sends = { BufferAllocation.class, AllocationRevoke.class },
      accepts = { BufferRegistration.class,
                 BufferStateUpdate.class,
                 AllocationStatusUpdate.class,
                 ControlSpaceRevoke.class },
      cardinality = Cardinality.SINGLE)
public class OtherEndBatteryEnergyApp implements Endpoint {
    private static final Logger log = LoggerFactory.getLogger(OtherEndBatteryEnergyApp.class);
    private volatile Connection connection;

    public Connection getConnection() {
        return connection;
    }

    private final BlockingQueue<BufferRegistration<?>> bufferRegistrations = new LinkedBlockingQueue<BufferRegistration<?>>();
    private final BlockingQueue<BufferSystemDescription> bufferSystemDescriptions = new LinkedBlockingQueue<BufferSystemDescription>();
    private final BlockingQueue<BufferStateUpdate<?>> bufferStateUpdates = new LinkedBlockingQueue<BufferStateUpdate<?>>();
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
                    BufferRegistration<?> resourceMessage = (BufferRegistration<?>) message;
                    log.debug("Received BufferRegistration");
                    bufferRegistrations.add(resourceMessage);
                } else if (BufferSystemDescription.class.isAssignableFrom(message.getClass())) {
                    BufferSystemDescription resourceMessage = (BufferSystemDescription) message;
                    log.debug("Received BufferSystemDescription");
                    bufferSystemDescriptions.add(resourceMessage);
                } else if (BufferStateUpdate.class.isAssignableFrom(message.getClass())) {
                    BufferStateUpdate<?> untypedBufferStateUpdate = (BufferStateUpdate<?>) message;
                    BufferStateUpdate<Energy> resourceMessage = (BufferStateUpdate<Energy>) untypedBufferStateUpdate;
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
                    throw new AssertionError("Unknown message class : " + message.getClass());
                }

            }

            @Override
            public void disconnected() {
                OtherEndBatteryEnergyApp.this.connection = null;
            }
        };
    }

    public void sendAllocation(BufferSystemDescription bufferSystemDescription, BufferStateUpdate<?> bufferStateUpdate) {
        long now = bufferStateUpdate.getTimestamp().getTime();

        Collection<ActuatorBehaviour> actuators = bufferSystemDescription.getActuators();
        ActuatorBehaviour actuatorBehaviour = actuators.iterator().next();
        int actuatorId = actuatorBehaviour.getId();
        Collection<RunningMode<FillLevelFunction<RunningModeBehaviour>>> runningModes = actuatorBehaviour.getRunningModes();
        int runningModeId = BatteryMode.CHARGE.ordinal();
        Set<ActuatorAllocation> actuatorAllocations = new HashSet<ActuatorAllocation>();
        actuatorAllocations.add(new ActuatorAllocation(actuatorId, runningModeId, new Date(now + 5000)));
        BufferAllocation bufferAllocation = new BufferAllocation(bufferStateUpdate,
                                                                 new Date(now),
                                                                 false,
                                                                 actuatorAllocations);
        log.debug("constructed buffer allocation=" + bufferAllocation);
        connection.sendMessage(bufferAllocation);

    }

    public BufferRegistration getBufferRegistration() throws InterruptedException {
        BufferRegistration state = bufferRegistrations.poll(5, TimeUnit.SECONDS);
        Assert.assertNotNull(state);
        return state;
    }

    public BufferSystemDescription getBufferSystemDescription() throws InterruptedException {
        BufferSystemDescription state = bufferSystemDescriptions.poll(5, TimeUnit.SECONDS);
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
