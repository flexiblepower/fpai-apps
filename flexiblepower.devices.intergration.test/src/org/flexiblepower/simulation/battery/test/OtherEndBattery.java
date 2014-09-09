package org.flexiblepower.simulation.battery.test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.MessageHandler;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.ral.drivers.battery.BatteryControlParameters;
import org.flexiblepower.ral.drivers.battery.BatteryMode;
import org.flexiblepower.ral.drivers.battery.BatteryState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Port(name = "driver", accepts = BatteryState.class, sends = BatteryControlParameters.class)
public class OtherEndBattery implements Endpoint {
    private static final Logger log = LoggerFactory.getLogger(OtherEndBattery.class);

    private final class BatteryControlParametersImpl implements BatteryControlParameters {
        private final BatteryMode mode;

        private BatteryControlParametersImpl(BatteryMode mode) {
            this.mode = mode;
        }

        @Override
        public BatteryMode getMode() {
            return mode;
        }
    }

    private final BlockingQueue<BatteryState> messages = new LinkedBlockingQueue<BatteryState>();

    private volatile Connection connection;

    @Override
    public MessageHandler onConnect(Connection connection) {
        this.connection = connection;
        return new MessageHandler() {
            @Override
            public void handleMessage(Object message) {
                Assert.assertTrue(BatteryState.class.isAssignableFrom(message.getClass()));
                BatteryState batteryState = (BatteryState) message;
                log.debug("Received battery state with load {}", batteryState.getStateOfCharge());
                messages.add(batteryState);
            }

            @Override
            public void disconnected() {
                OtherEndBattery.this.connection = null;
            }
        };
    }

    public boolean isConnected() {
        return connection != null;
    }

    public BatteryState getState() throws InterruptedException {
        BatteryState state = messages.poll(5, TimeUnit.SECONDS);
        Assert.assertNotNull(state);
        return state;
    }

    public void expectedState(double expectedStateOfCharge) throws InterruptedException {
        double stateOfCharge = getState().getStateOfCharge();
        log.debug("Expecting {} current state {}", expectedStateOfCharge, stateOfCharge);
        Assert.assertEquals(expectedStateOfCharge, stateOfCharge, 0.00001);
    }

    public void startCharging() {
        connection.sendMessage(new BatteryControlParametersImpl(BatteryMode.CHARGE));
        messages.clear();
    }

    public void startDischarging() {
        connection.sendMessage(new BatteryControlParametersImpl(BatteryMode.DISCHARGE));
        messages.clear();
    }

    public void switchToIdle() {
        connection.sendMessage(new BatteryControlParametersImpl(BatteryMode.IDLE));
        messages.clear();
    }
}
