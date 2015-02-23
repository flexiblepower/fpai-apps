package net.powermatcher.fpai.test;

import java.lang.annotation.Annotation;

import org.flexiblepower.messaging.Cardinality;
import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.Port;

public class MockConnection implements Connection {

    private final String portName;
    private Object lastReceivedMessage;

    public MockConnection(String portName) {
        this.portName = portName;
    }

    @Override
    public void sendMessage(Object message) {
        lastReceivedMessage = message;
    }

    public Object getLastReceivedMessage() {
        return lastReceivedMessage;
    }

    @Override
    public Port getPort() {
        return new Port() {

            @Override
            public Class<? extends Annotation> annotationType() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public Class<?>[] sends() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public String name() {
                return portName;
            }

            @Override
            public Cardinality cardinality() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public Class<?>[] accepts() {
                // TODO Auto-generated method stub
                return null;
            }
        };
    }

    public void reset() {
        lastReceivedMessage = null;
    }

}
