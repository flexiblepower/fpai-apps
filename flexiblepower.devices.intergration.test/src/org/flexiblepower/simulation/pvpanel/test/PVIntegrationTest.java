package org.flexiblepower.simulation.pvpanel.test;

import java.io.IOException;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.unit.SI;

import org.flexiblepower.efi.uncontrolled.UncontrolledRegistration;
import org.flexiblepower.efi.uncontrolled.UncontrolledUpdate;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.simulation.pvpanel.PVSimulation;
import org.flexiblepower.simulation.test.SimulationTest;
import org.flexiblepower.uncontrolled.manager.UncontrolledManager;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.util.tracker.ServiceTracker;

public class PVIntegrationTest extends SimulationTest {
    private ServiceTracker<Endpoint, Endpoint> pvPanelTracker;
    private ServiceTracker<Endpoint, Endpoint> uncontrolledManagerTracker;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        pvPanelTracker = new ServiceTracker<Endpoint, Endpoint>(bundleContext,
                                                                bundleContext.createFilter("(testa=pvsim)"),
                                                                null);
        pvPanelTracker.open();

        uncontrolledManagerTracker = new ServiceTracker<Endpoint, Endpoint>(bundleContext,
                                                                            bundleContext.createFilter("(testb=pvsim)"),
                                                                            null);
        uncontrolledManagerTracker.open();

    }

    private volatile Configuration simConfig;
    private volatile ServiceRegistration<Endpoint> otherEndRegistration;
    private volatile PVSimulation pvSimulation;
    private Configuration managerConfig;
    private UncontrolledManager uncontrolledManager;

    private OtherEndPVPanelApp create(int updateFrequency,
                                      double powerWhenStandBy,
                                      double powerWhenCloudy,
                                      double powerWhenSunny) throws Exception {
        simConfig = configAdmin.createFactoryConfiguration("org.flexiblepower.simulation.pvpanel.PVSimulation",
                                                           null);
        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put("updateFrequency", updateFrequency);
        properties.put("powerWhenStandBy", powerWhenStandBy);
        properties.put("powerWhenCloudy", powerWhenCloudy);
        properties.put("powerWhenSunny", powerWhenSunny);
        properties.put("testa", "pvsim");
        simConfig.update(properties);

        pvSimulation = (PVSimulation) pvPanelTracker.waitForService(1000);

        assertNotNull(pvSimulation);

        managerConfig = configAdmin.createFactoryConfiguration("org.flexiblepower.uncontrolled.manager.UncontrolledManager",
                                                               null);
        Dictionary<String, Object> managerProperties = new Hashtable<String, Object>();
        managerProperties.put("showWidget", "true");
        managerProperties.put("resourceId", "uncontrolled");
        managerProperties.put("expirationTime", "30");
        managerProperties.put("testb", "pvsim");
        managerConfig.update(managerProperties);
        uncontrolledManager = (UncontrolledManager) uncontrolledManagerTracker.waitForService(1000);
        assertNotNull(uncontrolledManager);

        OtherEndPVPanelApp otherEnd = new OtherEndPVPanelApp();
        otherEndRegistration = bundleContext.registerService(Endpoint.class, otherEnd, null);

        connectionManager.autoConnect();

        simulation.startSimulation(new Date(), 1);

        // PowerState initialState = otherEnd.getState();
        // assertEquals(selfDischargePower, initialState.getSelfDischargeSpeed().doubleValue(SI.WATT), 0.01);
        // TODO add assertions.... (What can we assert here?)

        return otherEnd;
    }

    @Override
    protected void tearDown() throws Exception {
        destroy();
        pvPanelTracker.close();
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
        OtherEndPVPanelApp otherEnd = create(1, 0.0, 200.0, 1500.0);
        assertNotNull(otherEnd.getConnection());
    }

    public void testRegistration() throws Exception {
        OtherEndPVPanelApp otherEnd = create(1, 0.0, 200.0, 1500.0);
        UncontrolledRegistration registration = otherEnd.getUncontrolledRegistration();
        Measurable<Duration> allocationDelay = registration.getAllocationDelay();
        assertEquals(allocationDelay, Measure.valueOf(5, SI.SECOND));
        assertNotNull(registration);
    }

    public void testUpdate() throws Exception {
        OtherEndPVPanelApp otherEnd = create(1, 0.0, 200.0, 1500.0);
        UncontrolledUpdate uncontrolledUpdate = otherEnd.getUncontrolledUpdate();
        assertNotNull(uncontrolledUpdate);

        assertNotNull(uncontrolledUpdate.getValidFrom());
        assertNotNull(uncontrolledUpdate.getTimestamp());
    }

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
