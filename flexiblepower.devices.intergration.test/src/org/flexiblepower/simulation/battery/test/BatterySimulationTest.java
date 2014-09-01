package org.flexiblepower.simulation.battery.test;

import java.io.IOException;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.ral.drivers.battery.BatteryState;
import org.flexiblepower.simulation.test.SimulationTest;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.util.tracker.ServiceTracker;

public class BatterySimulationTest extends SimulationTest {
    private ServiceTracker<Endpoint, Endpoint> batterySimulationTracker;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        batterySimulationTracker = new ServiceTracker<Endpoint, Endpoint>(bundleContext,
                                                                          bundleContext.createFilter("(test=batterysim)"),
                                                                          null);
        batterySimulationTracker.open();
    }

    @Override
    protected void tearDown() throws Exception {
        destroy();
        batterySimulationTracker.close();
        super.tearDown();
    }

    private volatile Configuration config;
    private volatile ServiceRegistration<Endpoint> otherEndRegistration;

    private OtherEnd create(long updateInterval,
                            double totalCapacity,
                            double initialStateOfCharge,
                            long chargePower,
                            long dischargePower,
                            double chargeEfficiency,
                            double dischargeEfficiency,
                            long selfDischargePower) throws Exception {
        config = configAdmin.createFactoryConfiguration("org.flexiblepower.simulation.battery.BatterySimulation", null);
        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put("updateInterval", updateInterval);
        properties.put("totalCapacity", totalCapacity);
        properties.put("initialStateOfCharge", initialStateOfCharge);
        properties.put("chargePower", chargePower);
        properties.put("dischargePower", dischargePower);
        properties.put("chargeEfficiency", chargeEfficiency);
        properties.put("dischargeEfficiency", dischargeEfficiency);
        properties.put("selfDischargePower", selfDischargePower);
        properties.put("test", "batterysim");
        config.update(properties);

        assertNotNull(batterySimulationTracker.waitForService(1000));

        OtherEnd otherEnd = new OtherEnd();
        otherEndRegistration = bundleContext.registerService(Endpoint.class, otherEnd, null);

        connectionManager.autoConnect();

        simulation.startSimulation(new Date(), 50);

        BatteryState initialState = otherEnd.getState();
        assertEquals(totalCapacity, initialState.getTotalCapacity().doubleValue(NonSI.KWH));
        assertEquals(initialStateOfCharge, initialState.getStateOfCharge(), 0.1);
        assertEquals(chargePower, initialState.getChargeSpeed().doubleValue(SI.WATT), 0.1);
        assertEquals(dischargePower, initialState.getDischargeSpeed().doubleValue(SI.WATT), 0.1);
        assertEquals(chargeEfficiency, initialState.getChargeEfficiency(), 0.01);
        assertEquals(dischargeEfficiency, initialState.getDischargeEfficiency(), 0.01);
        assertEquals(selfDischargePower, initialState.getSelfDischargeSpeed().doubleValue(SI.WATT), 0.01);

        return otherEnd;
    }

    private void destroy() throws IOException {
        simulation.stopSimulation();

        if (otherEndRegistration != null) {
            otherEndRegistration.unregister();
            otherEndRegistration = null;
        }
        if (config != null) {
            config.delete();
            config = null;
        }
    }

    public void testWithoutLeakage() throws Exception {
        OtherEnd otherEnd = create(1, 1, 0, 3600, 3600, 1, 1, 0);
        for (int i = 0; i < 100; i++) {
            otherEnd.expectedState(0);
        }
        otherEnd.startCharging();
        double expectedStateOfCharge = 0;
        for (int i = 0; i < 100; i++) {
            expectedStateOfCharge += 0.001;
            otherEnd.expectedState(expectedStateOfCharge);
        }
        otherEnd.stop();
        for (int i = 0; i < 100; i++) {
            otherEnd.expectedState(expectedStateOfCharge);
        }
        otherEnd.startDischarging();
        for (int i = 0; i < 100; i++) {
            expectedStateOfCharge -= 0.001;
            otherEnd.expectedState(expectedStateOfCharge);
        }
        for (int i = 0; i < 100; i++) {
            otherEnd.expectedState(0);
        }
    }

    public void testWithLeakage() throws Exception {
        OtherEnd otherEnd = create(1, 1, 0, 3600, 3600, 1, 1, 360);
        for (int i = 0; i < 100; i++) {
            otherEnd.expectedState(0);
        }
        otherEnd.startCharging();
        double expectedStateOfCharge = 0;
        for (int i = 0; i < 100; i++) {
            expectedStateOfCharge += 0.001;
            otherEnd.expectedState(expectedStateOfCharge);
        }
        otherEnd.stop();
        for (int i = 0; i < 100; i++) {
            expectedStateOfCharge -= 0.0001;
            otherEnd.expectedState(expectedStateOfCharge);
        }
        otherEnd.startDischarging();
        for (int i = 0; i < 100; i++) {
            expectedStateOfCharge = Math.max(0, expectedStateOfCharge - 0.001);
            otherEnd.expectedState(expectedStateOfCharge);
        }
        for (int i = 0; i < 100; i++) {
            otherEnd.expectedState(0);
        }
    }
}
