package org.flexiblepower.simulation.dishwasher.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.flexiblepower.efi.timeshifter.SequentialProfile;
import org.flexiblepower.efi.timeshifter.SequentialProfileAllocation;
import org.flexiblepower.efi.timeshifter.TimeShifterAllocation;
import org.flexiblepower.efi.timeshifter.TimeShifterRegistration;
import org.flexiblepower.efi.timeshifter.TimeShifterUpdate;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.rai.values.Commodity;
import org.flexiblepower.rai.values.CommodityForecast;
import org.flexiblepower.simulation.api.dishwasher.DishwasherSimulation;
import org.flexiblepower.simulation.test.SimulationTest;
import org.flexiblepower.time.TimeUtil;
import org.osgi.service.cm.Configuration;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DishwasherSimulationTest extends SimulationTest {
    private ServiceTracker<DishwasherSimulation, DishwasherSimulation> dishwasherSimulationTracker;
    private ServiceTracker<Endpoint, Endpoint> otherEndEnergyAppTracker;
    private static final Logger log = LoggerFactory.getLogger(DishwasherSimulationTest.class);

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        dishwasherSimulationTracker = new ServiceTracker<DishwasherSimulation, DishwasherSimulation>(bundleContext,
                                                                                                     DishwasherSimulation.class,
                                                                                                     null);
        dishwasherSimulationTracker.open();

        otherEndEnergyAppTracker = new ServiceTracker<Endpoint, Endpoint>(bundleContext,
                                                                          bundleContext.createFilter("(testc=otherendenergyapp)"),
                                                                          null);
        otherEndEnergyAppTracker.open();
    }

    private volatile Configuration simConfig;
    private volatile DishwasherSimulation dishwasherSimulation;
    private volatile Configuration managerConfig;
    private volatile Configuration energyappConfig;
    private volatile OtherEndEnergyApp energyapp;

    private void create() throws Exception {
        simConfig = configAdmin.createFactoryConfiguration("org.flexiblepower.simulation.dishwasher.DishwasherSimulationImpl",
                                                           null);
        Dictionary<String, Object> simProperties = new Hashtable<String, Object>();
        simProperties.put("resource.id", "dishwashersim");
        simProperties.put("testa", "dishwashersim");
        simConfig.update(simProperties);
        dishwasherSimulation = dishwasherSimulationTracker.waitForService(1000);
        assertNotNull(dishwasherSimulation);

        managerConfig = configAdmin.createFactoryConfiguration("org.flexiblepower.miele.dishwasher.manager.MieleDishwasherManager",
                                                               null);
        Dictionary<String, Object> managerProperties = new Hashtable<String, Object>();
        managerProperties.put("resourceId", "MieleDishWasherManager");
        managerProperties.put("showWidget", false);
        managerProperties.put("testb", "dishwasherman");
        managerConfig.update(managerProperties);

        energyappConfig = configAdmin.createFactoryConfiguration("org.flexiblepower.simulation.dishwasher.test.OtherEndEnergyApp",
                                                                 null);
        Dictionary<String, Object> energyappProperties = new Hashtable<String, Object>();
        energyappProperties.put("testc", "otherendenergyapp");
        energyappConfig.update(energyappProperties);
        energyapp = (OtherEndEnergyApp) otherEndEnergyAppTracker.waitForService(1000);
        assertNotNull(energyapp);

        connectAndStartSimulation(3);
    }

    @Override
    protected void tearDown() throws Exception {
        destroy();
        dishwasherSimulationTracker.close();
        otherEndEnergyAppTracker.close();
        energyapp.clearQueues();
        super.tearDown();
    }

    private void destroy() throws IOException {
        simulation.stopSimulation();
        if (energyappConfig != null) {
            energyappConfig.delete();
            energyappConfig = null;
        }
        if (managerConfig != null) {
            managerConfig.delete();
            managerConfig = null;
        }
        if (simConfig != null) {
            simConfig.delete();
            simConfig = null;
        }
    }

    private TimeShifterUpdate expectFutureUpdate() throws InterruptedException {
        TimeShifterUpdate timeshifterUpdate = energyapp.getTimeshifterUpdate();
        assertNotNull(timeshifterUpdate);
        assertNotNull(timeshifterUpdate.getResourceId());
        assertNotNull(timeshifterUpdate.getTimeShifterProfiles());
        return timeshifterUpdate;
    }

    public void testUpdate() throws Exception {
        create();

        dishwasherSimulation.setProgram("Test Program",
                                        TimeUtil.add(simulation.getTime(), Measure.valueOf(2, NonSI.HOUR)));

        expectFutureUpdate();
    }

    public void testSensorWashProgram() throws Exception {
        create();

        dishwasherSimulation.setProgram("Sensor Wash",
                                        TimeUtil.add(simulation.getTime(), Measure.valueOf(2, NonSI.HOUR)));

        TimeShifterUpdate timeshifterUpdate = expectFutureUpdate();
        List<SequentialProfile> timeShifterProfiles = timeshifterUpdate.getTimeShifterProfiles();

        // test number of timeshifterprofiles. (1 expected)
        assertEquals(timeShifterProfiles.size(), 1);
        SequentialProfile sequentialProfile = timeShifterProfiles.get(0);

        // test max interval before (0 expected, because there is only 1 timeshifterprofile)
        assertEquals(sequentialProfile.getMaxIntervalBefore().doubleValue(SI.SECOND), 0.0);

        CommodityForecast commodityForecast = sequentialProfile.getCommodityForecast();

        // test number of measureables in commodityforecasts (expected 3 in sensorwash programm)
        assertEquals(3, commodityForecast.size());

        // test durations
        assertEquals(commodityForecast.get(0).getDuration().doubleValue(NonSI.HOUR), 2.0);
        assertEquals(commodityForecast.get(1).getDuration().doubleValue(NonSI.HOUR), 0.5);
        assertEquals(commodityForecast.get(2).getDuration().doubleValue(NonSI.HOUR), 0.3);

        // test energy consumption
        assertEquals(commodityForecast.get(0).getValue().get(Commodity.ELECTRICITY).doubleValue(SI.WATT), 1000.0);
        assertEquals(commodityForecast.get(1).getValue().get(Commodity.ELECTRICITY).doubleValue(SI.WATT), 1500.0);
        assertEquals(commodityForecast.get(2).getValue().get(Commodity.ELECTRICITY).doubleValue(SI.WATT), 500.0);
    }

    public void testEnergySaveProgram() throws Exception {
        create();

        dishwasherSimulation.setProgram("Energy Save",
                                        TimeUtil.add(simulation.getTime(), Measure.valueOf(2, NonSI.HOUR)));

        TimeShifterUpdate timeshifterUpdate = expectFutureUpdate();
        List<SequentialProfile> timeShifterProfiles = timeshifterUpdate.getTimeShifterProfiles();

        // test number of timeshifterprofiles. (1 expected)
        assertEquals(timeShifterProfiles.size(), 1);
        SequentialProfile sequentialProfile = timeShifterProfiles.get(0);

        // test max interval before (0 expected, because there is only 1 timeshifterprofile)
        assertEquals(sequentialProfile.getMaxIntervalBefore().doubleValue(SI.SECOND), 0.0);
        CommodityForecast commodityForecast = sequentialProfile.getCommodityForecast();

        // test number of measureables in commodityforecasts (expected 2 in energy save programm)
        assertEquals(2, commodityForecast.size());

        // test durations
        assertEquals(commodityForecast.get(0).getDuration().doubleValue(NonSI.HOUR), 1.0);
        assertEquals(commodityForecast.get(1).getDuration().doubleValue(NonSI.HOUR), 1.0);

        // test energy consumption
        assertEquals(commodityForecast.get(0).getValue().get(Commodity.ELECTRICITY).doubleValue(SI.WATT), 1000.0);
        assertEquals(commodityForecast.get(1).getValue().get(Commodity.ELECTRICITY).doubleValue(SI.WATT), 500.0);
    }

    public void testLatestStartTime() throws Exception {
        create();
        dishwasherSimulation.setProgram("Test Program",
                                        TimeUtil.add(simulation.getTime(), Measure.valueOf(1, SI.SECOND)));
        TimeShifterUpdate updateBefore = expectFutureUpdate();
        log.debug("Update before: {}", updateBefore);
        TimeShifterUpdate updateAfter = expectFutureUpdate();
        log.debug("Update  after: {}", updateAfter);

        assertNotSame(updateBefore, updateAfter);
        assertNotNull(dishwasherSimulation.getLatestState().getStartTime()); // Started!
    }

    public void testAllocation() throws Exception {
        create();

        dishwasherSimulation.setProgram("Sensor Wash",
                                        TimeUtil.add(simulation.getTime(), Measure.valueOf(2, NonSI.YEAR)));

        log.debug("requesting registration");
        TimeShifterRegistration timeshifterRegistration = energyapp.getTimeshifterRegistration();
        log.debug("requesting update");
        TimeShifterUpdate update = energyapp.getTimeshifterUpdate();
        log.debug("update received");
        Measurable<Duration> allocationDelay = timeshifterRegistration.getAllocationDelay();
        assertEquals(allocationDelay, Measure.valueOf(5, SI.SECOND));
        assertNotNull(timeshifterRegistration);

        List<SequentialProfile> profiles = update.getTimeShifterProfiles();
        List<SequentialProfileAllocation> alocations = new ArrayList<SequentialProfileAllocation>();
        assertEquals(profiles.size(), 1);
        SequentialProfile profile = profiles.get(0);
        Date startTime = TimeUtil.add(simulation.getTime(), Measure.valueOf(4, SI.SECOND));
        alocations.add(new SequentialProfileAllocation(profile.getId(), startTime));

        // test whether the dishwasher is still turned off
        assertNull(dishwasherSimulation.getLatestState().getStartTime()); // Not Started!

        // create allocation to start now
        TimeShifterAllocation allocation = new TimeShifterAllocation(update, simulation.getTime(), false,
                                                                     alocations);

        log.debug("sending allocation");
        energyapp.sendAllocation(allocation);

        expectFutureUpdate();

        // test to see whether it has started
        assertNotNull(dishwasherSimulation.getLatestState().getStartTime()); // Started!
    }
}
