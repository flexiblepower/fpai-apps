package org.flexiblepower.simulation.battery.test;

import java.io.IOException;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;

import org.flexiblepower.battery.manager.BatteryManager;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.simulation.battery.BatterySimulation;
import org.flexiblepower.simulation.test.SimulationTest;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.util.tracker.ServiceTracker;

public class BatteryIntegrationTest extends SimulationTest {
    private ServiceTracker<Endpoint, Endpoint> batteryTracker;
    private ServiceTracker<Endpoint, Endpoint> bufferManagerTracker;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        batteryTracker = new ServiceTracker<Endpoint, Endpoint>(bundleContext,
                                                                bundleContext.createFilter("(testa=batterysim)"),
                                                                null);
        batteryTracker.open();

        bufferManagerTracker = new ServiceTracker<Endpoint, Endpoint>(bundleContext,
                                                                      bundleContext.createFilter("(testb=batterysim)"),
                                                                      null);
        bufferManagerTracker.open();

    }

    private volatile Configuration simConfig;
    private volatile ServiceRegistration<Endpoint> otherEndRegistration;
    private volatile BatterySimulation batterySimulation;
    private Configuration managerConfig;
    private BatteryManager batteryManager;

    private OtherEndBatteryEnergyApp create(long updateInterval,
                                            double totalCapacity,
                                            double initialStateOfCharge,
                                            long chargePower,
                                            long dischargePower,
                                            double chargeEfficiency,
                                            double dischargeEfficiency,
                                            long selfDischargePower

            ) throws Exception {
        // config battery simulation
        simConfig = configAdmin.createFactoryConfiguration("org.flexiblepower.simulation.battery.BatterySimulation",
                                                           null);
        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put("updateInterval", updateInterval);
        properties.put("totalCapacity", totalCapacity);
        properties.put("initialStateOfCharge", initialStateOfCharge);
        properties.put("chargePower", chargePower);
        properties.put("dischargePower", dischargePower);
        properties.put("chargeEfficiency", chargeEfficiency);
        properties.put("dischargeEfficiency", dischargeEfficiency);
        properties.put("selfDischargePower", selfDischargePower);
        properties.put("testa", "batterysim");
        simConfig.update(properties);
        batterySimulation = (BatterySimulation) batteryTracker.waitForService(5000);
        assertNotNull(batterySimulation);

        // config battery manager
        managerConfig = configAdmin.createFactoryConfiguration("org.flexiblepower.battery.manager.BatteryManager",
                                                               null);
        Dictionary<String, Object> managerProperties = new Hashtable<String, Object>();
        managerProperties.put("showWidget", "true");
        managerProperties.put("resourceID", "battery");
        managerProperties.put("expirationTime", "30");
        managerProperties.put("testb", "batterysim");
        managerConfig.update(managerProperties);
        batteryManager = (BatteryManager) bufferManagerTracker.waitForService(5000);
        assertNotNull(batteryManager);

        // set up the other end = energy app simulation
        OtherEndBatteryEnergyApp otherEnd = new OtherEndBatteryEnergyApp();
        otherEndRegistration = bundleContext.registerService(Endpoint.class, otherEnd, null);

        connectionManager.autoConnect();

        simulation.startSimulation(new Date(), 100);

        // PowerState initialState = otherEnd.getState();
        // assertEquals(selfDischargePower, initialState.getSelfDischargeSpeed().doubleValue(SI.WATT), 0.01);
        // TODO add assertions.... (What can we assert here?)

        return otherEnd;
    }

    @Override
    protected void tearDown() throws Exception {
        destroy();
        batteryTracker.close();
        super.tearDown();
    }

    private void destroy() throws IOException {
        simulation.stopSimulation();

        if (otherEndRegistration != null) {
            otherEndRegistration.unregister();
            otherEndRegistration = null;
        }
        if (simConfig != null) {
            simConfig.delete();
            simConfig = null;
        }
        if (managerConfig != null) {
            managerConfig.delete();
            managerConfig = null;
        }
    }

    public void testAutoconnect() throws Exception {
        OtherEndBatteryEnergyApp otherEnd = create(5L, 1, 0.7, 1500L, 1500L, 0.9, 0.9, 50L);
        assertNotNull(otherEnd.getConnection());
    }
    //
    // public void testRegistration() throws Exception {
    // OtherEndBatteryApp otherEnd = create(1, 0.0, 200.0, 1500.0);
    // UncontrolledRegistration registration = otherEnd.getBufferRegistration();
    // Measurable<Duration> allocationDelay = registration.getAllocationDelay();
    // assertEquals(allocationDelay, Measure.valueOf(5, SI.SECOND));
    // assertNotNull(registration);
    // }
    //
    // public void testUpdate() throws Exception {
    // OtherEndBatteryApp otherEnd = create(1, 0.0, 200.0, 1500.0);
    // UncontrolledUpdate uncontrolledUpdate = otherEnd.getBufferStateUpdate();
    // assertNotNull(uncontrolledUpdate);
    //
    // assertNotNull(uncontrolledUpdate.getValidFrom());
    // assertNotNull(uncontrolledUpdate.getTimestamp());
    // assertEquals(5, uncontrolledUpdate.getAllocationDelay().longValue(SI.SECOND));
    //
    // }

    // public void testMeasurement() throws Exception{
    // OtherEndPVPanelApp otherEnd = create(1,0.0,200,1500);
    // AllocationStatusUpdate update = otherEnd.getAllocationStatusUpdate();
    //
    // }
    //

    // public void testPrograms() throws Exception {
    // OtherEndEnergyApp otherEnd = create(1, true, "", "2014-09-11 15:30", "Energy Save", true);
    // TimeShifterUpdate timeshifterUpdate = otherEnd.getTimeshifterUpdate();
    // assertNotNull(timeshifterUpdate);
    // Date validFrom = timeshifterUpdate.getValidFrom();
    // List<SequentialProfile> timeShifterProfiles = timeshifterUpdate.getTimeShifterProfiles();
    // Map commodityProfiles = timeShifterProfiles.get(0).getCommodityProfiles();
    // assertNotNull(commodityProfiles);
    // /* not testing further now, as the commodityProfiles will change with the new EFI */
    // // TODO: write more tests
    // }
    //
    // public void testStart() throws Exception {
    // OtherEndEnergyApp otherEnd = create(1, true, "", "2013-01-01 12:00", "Aan", true); // in the past!
    // TimeShifterRegistration timeshifterRegistration = otherEnd.getTimeshifterRegistration();
    // Measurable<Duration> allocationDelay = timeshifterRegistration.getAllocationDelay();
    // assertEquals(allocationDelay, Measure.valueOf(5, SI.SECOND));
    // assertNotNull(timeshifterRegistration);
    // // TODO: test whether commodity profiles are changed
    // assertNotNull(pvSimulation.getCurrentState().getStartTime()); // Started!
    // }

}
