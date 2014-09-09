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

    private final double TEN_WATT = 10;
    private final double ONE_THOUSANDS_WATT = 0.001;

    private OtherEndBattery create(long updateInterval,
                                   double totalCapacity,
                                   double initialStateOfCharge,
                                   double chargePower,
                                   double dischargePower,
                                   double chargeEfficiency,
                                   double dischargeEfficiency,
                                   double selfDischargePower) throws Exception {
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

        // wait until batterySimulation service is running
        assertNotNull(batterySimulationTracker.waitForService(1000));

        OtherEndBattery otherEndBattery = new OtherEndBattery();
        otherEndRegistration = bundleContext.registerService(Endpoint.class, otherEndBattery, null);

        // connect BatterySimulation with OtherEndBattery
        connectionManager.autoConnect();

        simulation.startSimulation(new Date(), 10);

        // set parameters
        BatteryState initialState = otherEndBattery.getState();
        assertEquals(totalCapacity, initialState.getTotalCapacity().doubleValue(NonSI.KWH));
        assertEquals(initialStateOfCharge, initialState.getStateOfCharge(), 0.1);
        assertEquals(chargePower, initialState.getChargeSpeed().doubleValue(SI.WATT), TEN_WATT);
        assertEquals(dischargePower, initialState.getDischargeSpeed().doubleValue(SI.WATT), TEN_WATT);
        assertEquals(chargeEfficiency, initialState.getChargeEfficiency(), 0.01);
        assertEquals(dischargeEfficiency, initialState.getDischargeEfficiency(), 0.01);
        assertEquals(selfDischargePower, initialState.getSelfDischargeSpeed().doubleValue(SI.WATT), 0.01);

        return otherEndBattery;
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
        // initial state set up
        final int ONE_SECONDEN = 1;
        final double ONE_KWH = 1;
        final double EMPTY = 0;
        OtherEndBattery otherEndBattery = create(ONE_SECONDEN, ONE_KWH, EMPTY, TEN_WATT, TEN_WATT, 0, 0, 0);
        otherEndBattery.expectedState(0);

        // charging state with chargepower and no leakage
        otherEndBattery.startCharging();
        double expectedStateOfCharge = 0;
        for (int i = 0; i < 100; i++) {
            // calculate chargepower relative to the total capacity
            expectedStateOfCharge += TEN_WATT / ONE_KWH / 3600.0 / 1000.0;
            otherEndBattery.expectedState(expectedStateOfCharge);
        }

        // in idle mode nothing changes
        otherEndBattery.switchToIdle();
        for (int i = 0; i < 10; i++) {
            otherEndBattery.expectedState(expectedStateOfCharge);
        }

        // discharging
        otherEndBattery.startDischarging();
        for (int i = 0; i < 100; i++) {
            // calculate chargepower relative to the total capacity
            expectedStateOfCharge -= TEN_WATT / ONE_KWH / 3600.0 / 1000.0;
            otherEndBattery.expectedState(expectedStateOfCharge);
        }

        // in idle mode nothing changes
        otherEndBattery.switchToIdle();
        for (int i = 0; i < 10; i++) {
            otherEndBattery.expectedState(0);
        }
    }

    public void testWithLeakage() throws Exception {
        // initial state set up
        final int ONE_SECONDEN = 1;
        final double ONE_KWH = 1;
        final double EMPTY = 0;
        OtherEndBattery otherEndBattery = create(ONE_SECONDEN,
                                                 ONE_KWH,
                                                 EMPTY,
                                                 TEN_WATT,
                                                 TEN_WATT,
                                                 0,
                                                 0,
                                                 ONE_THOUSANDS_WATT);
        otherEndBattery.expectedState(0);

        // charging state with chargepower and no leakage
        otherEndBattery.startCharging();
        double expectedStateOfCharge = 0;
        for (int i = 0; i < 100; i++) {
            // calculate chargepower relative to the total capacity
            expectedStateOfCharge += (TEN_WATT - ONE_THOUSANDS_WATT) / ONE_KWH / 3600.0 / 1000.0;
            otherEndBattery.expectedState(expectedStateOfCharge);
        }

        // in idle mode nothing changes
        otherEndBattery.switchToIdle();
        for (int i = 0; i < 10; i++) {
            otherEndBattery.expectedState(expectedStateOfCharge);
        }

        // discharging
        otherEndBattery.startDischarging();
        for (int i = 0; i < 100; i++) {
            // calculate chargepower relative to the total capacity
            expectedStateOfCharge -= (TEN_WATT + ONE_THOUSANDS_WATT) / ONE_KWH / 3600.0 / 1000.0;
            otherEndBattery.expectedState(expectedStateOfCharge);
        }

        // in idle mode nothing changes
        otherEndBattery.switchToIdle();
        for (int i = 0; i < 10; i++) {
            otherEndBattery.expectedState(0);
        }
    }
}
