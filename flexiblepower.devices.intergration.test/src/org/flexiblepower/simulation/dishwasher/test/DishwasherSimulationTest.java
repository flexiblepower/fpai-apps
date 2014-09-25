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
import org.flexiblepower.rai.values.CommoditySet;
import org.flexiblepower.simulation.dishwasher.DishwasherSimulation;
import org.flexiblepower.simulation.test.SimulationTest;
import org.flexiblepower.time.TimeUtil;
import org.osgi.service.cm.Configuration;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DishwasherSimulationTest extends SimulationTest {
    private ServiceTracker<Endpoint, Endpoint> dishwasherSimulationTracker;
    private ServiceTracker<Endpoint, Endpoint> otherEndEnergyAppTracker;
    private static final Logger log = LoggerFactory.getLogger(DishwasherSimulationTest.class);

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        dishwasherSimulationTracker = new ServiceTracker<Endpoint, Endpoint>(bundleContext,
                                                                             bundleContext.createFilter("(testa=dishwashersim)"),
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

    private void create(int updateFrequency,
                        boolean isConnected,
                        String startTime,
                        String latestStartTime,
                        String program,
                        boolean showWidget) throws Exception {
        simConfig = configAdmin.createFactoryConfiguration("org.flexiblepower.simulation.dishwasher.DishwasherSimulation",
                                                           null);
        Dictionary<String, Object> simProperties = new Hashtable<String, Object>();
        simProperties.put("isConnected", isConnected);
        simProperties.put("startTime", startTime);
        simProperties.put("latestStartTime", latestStartTime);
        simProperties.put("program", program);
        simProperties.put("testa", "dishwashersim");
        simConfig.update(simProperties);
        dishwasherSimulation = (DishwasherSimulation) dishwasherSimulationTracker.waitForService(1000);
        assertNotNull(dishwasherSimulation);

        managerConfig = configAdmin.createFactoryConfiguration("org.flexiblepower.miele.dishwasher.manager.MieleDishwasherManager",
                                                               null);
        Dictionary<String, Object> managerProperties = new Hashtable<String, Object>();
        managerProperties.put("resourceId", "MieleDishWasherManager");
        managerProperties.put("showWidget", showWidget);
        managerProperties.put("testb", "dishwasherman");
        managerConfig.update(managerProperties);

        energyappConfig = configAdmin.createFactoryConfiguration("org.flexiblepower.simulation.dishwasher.test.OtherEndEnergyApp",
                                                                 null);
        Dictionary<String, Object> energyappProperties = new Hashtable<String, Object>();
        energyappProperties.put("testc", "otherendenergyapp");
        energyappConfig.update(energyappProperties);
        energyapp = (OtherEndEnergyApp) otherEndEnergyAppTracker.waitForService(1000);
        assertNotNull(energyapp);

        for (int i = 0; i < 10; i++) {
            if (connectionManager.getEndpoints().size() < 3) {
                Thread.sleep(50);
            } else {
                break;
            }
        }
        if (connectionManager.getEndpoints().size() < 3) {
            fail("Not all endpoints are picked up by the connection manager");
        }
        connectionManager.autoConnect();

        simulation.startSimulation(new Date(), 10000);
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

    public void testRegistration() throws Exception {
        create(1, true, "", "2014-09-11 15:30", "Aan", true);
        TimeShifterRegistration timeshifterRegistration = energyapp.getTimeshifterRegistration();
        Measurable<Duration> allocationDelay = timeshifterRegistration.getAllocationDelay();
        assertEquals(allocationDelay, Measure.valueOf(5, SI.SECOND));
        assertNotNull(timeshifterRegistration);
        // otherEnd.clearQueues();
    }

    public void testUpdate() throws Exception {
        create(1, true, "", "2014-09-11 15:30", "Aan", true);
        TimeShifterRegistration timeshifterRegistration = energyapp.getTimeshifterRegistration();
        TimeShifterUpdate timeshifterUpdate = energyapp.getTimeshifterUpdate();
        assertNotNull(timeshifterUpdate);
        Date validFrom = timeshifterUpdate.getValidFrom();
        List<SequentialProfile> timeShifterProfiles = timeshifterUpdate.getTimeShifterProfiles();
        assertNotNull(timeShifterProfiles);
        // otherEnd.clearQueues();
    }

    public void testSensorWashProgram() throws Exception {
        create(1, true, "", "2014-09-11 15:30", "Sensor Wash", true);
        TimeShifterUpdate timeshifterUpdate = energyapp.getTimeshifterUpdate();
        assertNotNull(timeshifterUpdate);
        Date validFrom = timeshifterUpdate.getValidFrom();
        List<SequentialProfile> timeShifterProfiles = timeshifterUpdate.getTimeShifterProfiles();
        Date endBefore = timeshifterUpdate.getEndBefore();

        // test number of timeshifterprofiles. (1 expected)
        assertEquals(timeShifterProfiles.size(), 1);
        SequentialProfile sequentialProfile = timeShifterProfiles.get(0);

        // test max interval before (0 expected, because there is only 1 timeshifterprofile)
        assertEquals(sequentialProfile.getMaxIntervalBefore().doubleValue(SI.SECOND), 0.0);

        CommodityForecast commodityForecast = sequentialProfile.getCommodityForecast();
        Measurable<Duration> totalDuration = commodityForecast.getTotalDuration();
        CommoditySet commodities = commodityForecast.getCommodities();

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

        // otherEnd.clearQueues();
    }

    public void testEnergySaveProgram() throws Exception {
        create(1, true, "", "2014-09-11 15:30", "Energy Save", true);
        TimeShifterUpdate timeshifterUpdate = energyapp.getTimeshifterUpdate();
        assertNotNull(timeshifterUpdate);
        Date validFrom = timeshifterUpdate.getValidFrom();
        List<SequentialProfile> timeShifterProfiles = timeshifterUpdate.getTimeShifterProfiles();
        Date endBefore = timeshifterUpdate.getEndBefore();

        // test number of timeshifterprofiles. (1 expected)
        assertEquals(timeShifterProfiles.size(), 1);
        SequentialProfile sequentialProfile = timeShifterProfiles.get(0);

        // test max interval before (0 expected, because there is only 1 timeshifterprofile)
        assertEquals(sequentialProfile.getMaxIntervalBefore().doubleValue(SI.SECOND), 0.0);

        CommodityForecast commodityForecast = sequentialProfile.getCommodityForecast();
        Measurable<Duration> totalDuration = commodityForecast.getTotalDuration();
        CommoditySet commodities = commodityForecast.getCommodities();

        // test number of measureables in commodityforecasts (expected 2 in energy save programm)
        assertEquals(2, commodityForecast.size());

        // test durations
        assertEquals(commodityForecast.get(0).getDuration().doubleValue(NonSI.HOUR), 1.0);
        assertEquals(commodityForecast.get(1).getDuration().doubleValue(NonSI.HOUR), 1.0);

        // test energy consumption
        assertEquals(commodityForecast.get(0).getValue().get(Commodity.ELECTRICITY).doubleValue(SI.WATT), 1000.0);
        assertEquals(commodityForecast.get(1).getValue().get(Commodity.ELECTRICITY).doubleValue(SI.WATT), 500.0);

        // otherEnd.clearQueues();
    }

    public void testLatestStartTime() throws Exception {
        create(1, true, "", "2013-01-01 12:00", "Aan", true); // in the past!
        TimeShifterRegistration timeshifterRegistration = energyapp.getTimeshifterRegistration();
        Measurable<Duration> allocationDelay = timeshifterRegistration.getAllocationDelay();
        assertEquals(allocationDelay, Measure.valueOf(5, SI.SECOND));
        assertNotNull(timeshifterRegistration);
        // TODO: test whether commodity profiles are changed
        assertNotNull(dishwasherSimulation.getCurrentState().getStartTime()); // Started!
        // otherEnd.clearQueues();

    }

    public void testStart() throws Exception {
        create(1, true, "", "2013-01-01 12:00", "Aan", true);
        log.debug("requesting registration");
        TimeShifterRegistration timeshifterRegistration = energyapp.getTimeshifterRegistration();
        log.debug("requesting update");
        TimeShifterUpdate update = energyapp.getTimeshifterUpdate();
        log.debug("update received");
        Measurable<Duration> allocationDelay = timeshifterRegistration.getAllocationDelay();
        assertEquals(allocationDelay, Measure.valueOf(5, SI.SECOND));
        assertNotNull(timeshifterRegistration);
        // TODO: test whether commodity profiles are changed

        List<SequentialProfile> profiles = update.getTimeShifterProfiles();
        List<SequentialProfileAllocation> alocations = new ArrayList<SequentialProfileAllocation>();
        assertEquals(profiles.size(), 1);
        SequentialProfile profile = profiles.get(0);
        Date startTime = TimeUtil.add(simulation.getTime(), Measure.valueOf(4, SI.SECOND));
        alocations.add(new SequentialProfileAllocation(profile.getId(), startTime));
        Measurable<Duration> totalDuration = profile.getCommodityForecast().getTotalDuration();

        TimeShifterAllocation allocation = new TimeShifterAllocation(update, simulation.getTime(), false,
                                                                     alocations);

        log.debug("sending allocation");
        energyapp.sendAllocation(allocation);

        update = energyapp.getTimeshifterUpdate();
        List<SequentialProfile> newSeqProfs = update.getTimeShifterProfiles();
        assertEquals(newSeqProfs.size(), 1);
        SequentialProfile newSeqProf = newSeqProfs.get(0);
        CommodityForecast newComForcast = newSeqProf.getCommodityForecast();
        // endBefore = newComForcast.get
        assertEquals(2, newComForcast.size());
        // newSeqProf.

        // log.debug("end before: {}", new SimpleDateFormat("yyMMddHHmm").format(endBefore));
        // log.debug("estimated end: {} ", new SimpleDateFormat("yyMMddHHmm").format(TimeUtil.add(startTime,
        // totalDuration)));
        // assertTrue(new SimpleDateFormat("yyMMddHHmm").format(endBefore).equals(new
        // SimpleDateFormat("yyMMddHHmm").format(TimeUtil.add(startTime,
        // totalDuration))));

    }

    // public void testUpdates() throws Exception {
    //
    // Date latestStartTime = simulation.getTime();
    // TimeUtil.add(latestStartTime, Measure.valueOf(30, SI.SECOND));
    // String latestStartTimeString = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(latestStartTime);
    //
    // OtherEndEnergyApp otherEnd = create(1, true, latestStartTimeString, latestStartTimeString, "Energy Save", true);
    //
    // assertEquals(0, getConsumptionMeasure(otherEnd));
    // // TODO: test when the dishwasher starts...
    //
    // // otherEnd.clearQueues();
    //
    // }

    // private double getConsumptionMeasure(OtherEndEnergyApp otherEnd) throws InterruptedException {
    //
    // TimeShifterUpdate update = otherEnd.getTimeshifterUpdate();
    // assertNotNull(update);
    // assertNotNull(update.getValidFrom());
    // assertNotNull(update.getTimestamp());
    //
    // List<SequentialProfile> profiles = update.getTimeShifterProfiles();
    // SequentialProfile profile = profiles.get(0);
    // CommodityUncertainMeasurables commodityMeasurable = profile.getCommodityForecast().get(0).getValue();
    // UncertainMeasure<Power> measure = commodityMeasurable.get(Commodity.ELECTRICITY);
    // return measure.getMean().doubleValue(SI.WATT);
    //
    // }

}
