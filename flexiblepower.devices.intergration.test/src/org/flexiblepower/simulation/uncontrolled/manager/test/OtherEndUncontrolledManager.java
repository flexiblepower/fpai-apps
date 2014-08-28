package org.flexiblepower.simulation.uncontrolled.manager.test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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
    private final BlockingQueue<ResourceMessage> messages = new LinkedBlockingQueue<ResourceMessage>();

    @Override
    public MessageHandler onConnect(Connection connection) {
        this.connection = connection;
        return new MessageHandler() {
            @Override
            public void handleMessage(Object message) {
                log.info("message received");
                Assert.assertTrue(ResourceMessage.class.isAssignableFrom(message.getClass()));
                ResourceMessage resourceMessage = (ResourceMessage) message;
                log.debug("Received uncontrolled resourceMessage");
                messages.add(resourceMessage);
            }

            @Override
            public void disconnected() {
                OtherEndUncontrolledManager.this.connection = null;
            }
        };
    }

}
