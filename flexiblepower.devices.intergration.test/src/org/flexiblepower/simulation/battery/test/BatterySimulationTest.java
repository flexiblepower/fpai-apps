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
    private final double ONE_WATT = 1;

    private OtherEndBatteryManager create(long updateInterval,
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

        OtherEndBatteryManager otherEndBattery = new OtherEndBatteryManager();
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
        assertEquals(chargeEfficiency, initialState.getChargeEfficiency(), 0.9);
        assertEquals(dischargeEfficiency, initialState.getDischargeEfficiency(), 0.9);
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
        final int deltaT = 1; // Update interval of one second.
        final double ONE_KWH = 1;
        final double EMPTY = 0;
        double total_Capacity = ONE_KWH;

        OtherEndBatteryManager otherEndBattery = create(deltaT, total_Capacity, EMPTY, TEN_WATT, TEN_WATT, 0.9, 0.9, 0);
        double expectedStateOfCharge = 0;
        otherEndBattery.expectedState(expectedStateOfCharge);

        log.debug("------------ Start charging ---------------");
        // charging state with charge power and no leakage
        otherEndBattery.startCharging();
        for (int i = 0; i < 100; i++) {
            // calculate charge power relative to the total capacity
            expectedStateOfCharge += incrementStateOfCharge(TEN_WATT * deltaT, 0, total_Capacity);
            otherEndBattery.expectedState(expectedStateOfCharge);
        }

        log.debug("------------ Switch to IDLE ---------------");
        // in idle mode nothing changes
        otherEndBattery.switchToIdle();
        for (int i = 0; i < 10; i++) {
            otherEndBattery.expectedState(expectedStateOfCharge);
        }

        log.debug("------------ Switch to Discharging ---------------");
        // discharging
        otherEndBattery.startDischarging();
        for (int i = 0; i < 100; i++) {
            // calculate discharge power relative to the total capacity
            expectedStateOfCharge -= incrementStateOfCharge(TEN_WATT * deltaT, 0, total_Capacity);
            otherEndBattery.expectedState(expectedStateOfCharge);
        }

        log.debug("------------ Switch to IDLE ---------------");
        // in idle mode nothing changes
        otherEndBattery.switchToIdle();
        for (int i = 0; i < 10; i++) {
            otherEndBattery.expectedState(0);
        }
    }

    public void testWithLeakage() throws Exception {
        // initial state set up
        final int deltaT = 1; // Duration of one second.
        final double ONE_KWH = 1;
        final double EMPTY = 0;
        double total_Capacity = ONE_KWH;
        double leakage = ONE_WATT;
        OtherEndBatteryManager otherEndBattery = create(deltaT,
                                                        total_Capacity,
                                                        EMPTY,
                                                        TEN_WATT * deltaT,
                                                        TEN_WATT * deltaT,
                                                        0.9,
                                                        0.9,
                                                        leakage);
        double expectedStateOfCharge = 0;
        otherEndBattery.expectedState(expectedStateOfCharge);

        log.debug("------------ Start charging ---------------");
        // charging state with charge power and no leakage
        otherEndBattery.startCharging();
        for (int i = 0; i < 100; i++) {
            // calculate charge power relative to the total capacity
            expectedStateOfCharge += incrementStateOfCharge(TEN_WATT * deltaT, -leakage * deltaT, total_Capacity);
            otherEndBattery.expectedState(expectedStateOfCharge);
        }

        log.debug("------------ Switch to IDLE ---------------");
        // in idle mode nothing changes
        otherEndBattery.switchToIdle();
        for (int i = 0; i < 100; i++) {
            // calculate leakage relative to the total capacity
            expectedStateOfCharge += incrementStateOfCharge(0, -leakage * deltaT, total_Capacity);
            otherEndBattery.expectedState(expectedStateOfCharge);
        }

        log.debug("------------ Switch to Discharging ---------------");
        // discharging
        otherEndBattery.startDischarging();
        for (int i = 0; i < 100; i++) {
            // calculate discharge power relative to the total capacity
            expectedStateOfCharge += incrementStateOfCharge(-TEN_WATT * deltaT, -leakage * deltaT, total_Capacity);
            otherEndBattery.expectedState(expectedStateOfCharge);
        }

        log.debug("------------ Switch to IDLE ---------------");
        // in idle mode nothing changes
        otherEndBattery.switchToIdle();
        for (int i = 0; i < 10; i++) {
            // calculate leakage relative to the total capacity
            expectedStateOfCharge += incrementStateOfCharge(0, -leakage * deltaT, total_Capacity);
            otherEndBattery.expectedState(expectedStateOfCharge);
        }
    }

    /**
     * Calculate the relative increment of the SoC for a given amount of charge or discharge in WattSecond and a self
     * discharge in WattSecond.
     *
     * @param energy
     *            The amount of energy that is put in, or taken out in case the energy is negative, of the battery in
     *            WattSecond.
     * @param leakage
     *            The amount of self discharge of the battery in WattSecond.
     * @param totalCapacityInKWattHour
     *            The total capacity of the battery in KWattHour.
     * @return Relative change in SoC for the battery.
     */
    private double incrementStateOfCharge(double energy, double leakage, double totalCapacityInKWattHour) {
        if (energy < 0) {
            energy = 0;
        }
        else if (energy > totalCapacityInKWattHour * 1000.0 * 3600.0) {
            energy = totalCapacityInKWattHour * 1000.0 * 3600.0;
        }
        double incrementInWattSecond = energy + leakage;

        return incrementInWattSecond / 1000.0 / 3600.0;
    }
}
