package org.flexiblepower.simulation.dishwasher.test;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.efi.timeshifter.SequentialProfile;
import org.flexiblepower.efi.timeshifter.TimeShifterRegistration;
import org.flexiblepower.efi.timeshifter.TimeShifterUpdate;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.miele.dishwasher.manager.MieleDishwasherManager;
import org.flexiblepower.rai.values.Commodity;
import org.flexiblepower.rai.values.CommodityUncertainMeasurables;
import org.flexiblepower.rai.values.UncertainMeasure;
import org.flexiblepower.simulation.dishwasher.DishwasherSimulation;
import org.flexiblepower.simulation.test.SimulationTest;
import org.flexiblepower.time.TimeService;
import org.flexiblepower.time.TimeUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.util.tracker.ServiceTracker;

import aQute.bnd.annotation.component.Reference;

public class DishwasherSimulationTest extends SimulationTest {
    private ServiceTracker<Endpoint, Endpoint> dishwasherSimulationTracker;
    private ServiceTracker<Endpoint, Endpoint> dishwasherManagerTracker;
    private ServiceTracker<Endpoint, Endpoint> otherEndEnergyAppTracker;

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        dishwasherSimulationTracker = new ServiceTracker<Endpoint, Endpoint>(bundleContext,
                                                                             bundleContext.createFilter("(testa=dishwashersim)"),
                                                                             null);
        dishwasherSimulationTracker.open();

        dishwasherManagerTracker = new ServiceTracker<Endpoint, Endpoint>(bundleContext,
                                                                          bundleContext.createFilter("(testb=dishwasherman)"),
                                                                          null);
        dishwasherManagerTracker.open();

        otherEndEnergyAppTracker = new ServiceTracker<Endpoint, Endpoint>(bundleContext,
                                                                          bundleContext.createFilter("(testc=otherendenergyapp)"),
                                                                          null);
        otherEndEnergyAppTracker.open();
    }

    private volatile Configuration simConfig;
    private volatile ServiceRegistration<Endpoint> otherEndRegistration;
    private volatile DishwasherSimulation dishwasherSimulation;
    private volatile Configuration managerConfig;
    private volatile MieleDishwasherManager dishwasherManager;
    private volatile Configuration energyappConfig;
    private volatile OtherEndEnergyApp energyapp;

    private OtherEndEnergyApp create(int updateFrequency,
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
        managerProperties.put("showWidget", showWidget);
        managerProperties.put("testb", "dishwasherman");
        managerConfig.update(managerProperties);
        dishwasherManager = (MieleDishwasherManager) dishwasherManagerTracker.waitForService(1000);
        assertNotNull(dishwasherManager);

        energyappConfig = configAdmin.createFactoryConfiguration("org.flexiblepower.simulation.dishwasher.test.OtherEndEnergyApp",
                                                                 null);
        Dictionary<String, Object> energyappProperties = new Hashtable<String, Object>();
        energyappProperties.put("testc", "otherendenergyapp");
        energyappConfig.update(energyappProperties);
        energyapp = (OtherEndEnergyApp) otherEndEnergyAppTracker.waitForService(10000);
        assertNotNull(energyapp);
        // otherEndRegistration = bundleContext.registerService(Endpoint.class, energyapp, null);

        // OtherEndEnergyApp otherEnd = new OtherEndEnergyApp();

        connectionManager.autoConnect();

        simulation.startSimulation(new Date(), 1);

        // PowerState initialState = otherEnd.getState();
        // assertEquals(selfDischargePower, initialState.getSelfDischargeSpeed().doubleValue(SI.WATT), 0.01);
        // TODO add assertions.... (What can we assert here?)

        return energyapp;
    }

    @Override
    protected void tearDown() throws Exception {
        destroy();
        dishwasherSimulationTracker.close();
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
        OtherEndEnergyApp otherEnd = create(1, true, "", "2014-09-11 15:30", "Aan", true);
        assertNotNull(otherEnd.getConnection());
    }

    public void testRegistration() throws Exception {
        OtherEndEnergyApp otherEnd = create(1, true, "", "2014-09-11 15:30", "Aan", true);
        TimeShifterRegistration timeshifterRegistration = otherEnd.getTimeshifterRegistration();
        Measurable<Duration> allocationDelay = timeshifterRegistration.getAllocationDelay();
        assertEquals(allocationDelay, Measure.valueOf(5, SI.SECOND));
        assertNotNull(timeshifterRegistration);
    }

    public void testUpdate() throws Exception {
        OtherEndEnergyApp otherEnd = create(1, true, "", "2014-09-11 15:30", "Aan", true);
        TimeShifterUpdate timeshifterUpdate = otherEnd.getTimeshifterUpdate();
        assertNotNull(timeshifterUpdate);
        Date validFrom = timeshifterUpdate.getValidFrom();
        List<SequentialProfile> timeShifterProfiles = timeshifterUpdate.getTimeShifterProfiles();
        assertNotNull(timeShifterProfiles);
    }

    public void testPrograms() throws Exception {
        OtherEndEnergyApp otherEnd = create(1, true, "", "2014-09-11 15:30", "Energy Save", true);
        TimeShifterUpdate timeshifterUpdate = otherEnd.getTimeshifterUpdate();
        assertNotNull(timeshifterUpdate);
        Date validFrom = timeshifterUpdate.getValidFrom();
        List<SequentialProfile> timeShifterProfiles = timeshifterUpdate.getTimeShifterProfiles();
        // Map commodityProfiles = timeShifterProfiles.get(0).getCommodityProfiles();
        // assertNotNull(commodityProfiles);
        /* not testing further now, as the commodityProfiles will change with the new EFI */
        // TODO: write more tests
    }

    public void testStart() throws Exception {
        OtherEndEnergyApp otherEnd = create(1, true, "", "2013-01-01 12:00", "Aan", true); // in the past!
        TimeShifterRegistration timeshifterRegistration = otherEnd.getTimeshifterRegistration();
        Measurable<Duration> allocationDelay = timeshifterRegistration.getAllocationDelay();
        assertEquals(allocationDelay, Measure.valueOf(5, SI.SECOND));
        assertNotNull(timeshifterRegistration);
        // TODO: test whether commodity profiles are changed
        assertNotNull(dishwasherSimulation.getCurrentState().getStartTime()); // Started!
    }

    public void testUpdates() throws Exception {
        Date latestStartTime = timeService.getTime();
        TimeUtil.add(latestStartTime, Measure.valueOf(30, SI.SECOND));
        String latestStartTimeString = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(latestStartTime);

        OtherEndEnergyApp otherEnd = create(1, true, latestStartTimeString, latestStartTimeString, "Energy Save", true);

        assertEquals(0, getConsumptionMeasure(otherEnd));
        // TODO: test when the dishwasher starts...

    }

    private double getConsumptionMeasure(OtherEndEnergyApp otherEnd) throws InterruptedException {

        TimeShifterUpdate update = otherEnd.getTimeshifterUpdate();
        assertNotNull(update);
        assertNotNull(update.getValidFrom());
        assertNotNull(update.getTimestamp());

        List<SequentialProfile> profiles = update.getTimeShifterProfiles();
        SequentialProfile profile = profiles.get(0);
        CommodityUncertainMeasurables commodityMeasurable = profile.getCommodityForecast().get(0).getValue();
        UncertainMeasure<Power> measure = commodityMeasurable.get(Commodity.ELECTRICITY);
        return measure.getMean().doubleValue(SI.WATT);
    }

}
