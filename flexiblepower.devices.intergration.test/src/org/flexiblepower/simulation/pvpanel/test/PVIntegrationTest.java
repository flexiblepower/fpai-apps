package org.flexiblepower.simulation.pvpanel.test;

import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.unit.SI;

import junit.framework.Assert;

import org.flexiblepower.efi.uncontrolled.UncontrolledMeasurement;
import org.flexiblepower.efi.uncontrolled.UncontrolledRegistration;
import org.flexiblepower.efi.uncontrolled.UncontrolledUpdate;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.rai.values.Commodity;
import org.flexiblepower.rai.values.CommodityMeasurables;
import org.flexiblepower.simulation.pvpanel.PVSimulation;
import org.flexiblepower.simulation.pvpanel.Weather;
import org.flexiblepower.simulation.test.SimulationTest;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PVIntegrationTest extends SimulationTest {
    private static final Logger log = LoggerFactory.getLogger(PVIntegrationTest.class);
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

    private OtherEndPVPanelApp create(int updateDelay,
                                      double powerWhenStandBy,
                                      double powerWhenCloudy,
                                      double powerWhenSunny) throws Exception {
        simConfig = configAdmin.createFactoryConfiguration("org.flexiblepower.simulation.pvpanel.PVSimulation",
                                                           null);
        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put("updateDelay", updateDelay);
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

        OtherEndPVPanelApp otherEnd = new OtherEndPVPanelApp();
        otherEndRegistration = bundleContext.registerService(Endpoint.class, otherEnd, null);

        connectAndStartSimulation(3);

        // PowerState initialState = otherEnd.getState();
        // assertEquals(selfDischargePower, initialState.getSelfDischargeSpeed().doubleValue(SI.WATT), 0.01);
        // TODO add assertions.... (What can we assert here?)

        return otherEnd;
    }

    @Override
    protected void tearDown() throws Exception {
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
        pvPanelTracker.close();
        uncontrolledManagerTracker.close();
        super.tearDown();
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

    public void testMoonWeather() throws Exception {
        OtherEndPVPanelApp otherEnd = create(1, 0.0, 200.0, 1500.0);

        pvSimulation.setWeather(Weather.moon);
        ignoreMeasures(otherEnd, 10); // ignore some measures, to be sure that the weather is set correctly

        for (int i = 0; i < 100; i++) {
            double energyUsage = getConsumptionMeasure(otherEnd);
            assertEquals(-0.0, energyUsage);
        }
    }

    public void testCloudyWeather() throws Exception {
        OtherEndPVPanelApp otherEnd = create(1, 0.0, 200.0, 1500.0);

        pvSimulation.setWeather(Weather.clouds);
        ignoreMeasures(otherEnd, 10); // ignore some measures, to be sure that the weather is set correctly

        for (int i = 0; i < 100; i++) {
            double production = getConsumptionMeasure(otherEnd);
            double minExpectedProduction = -400;
            double maxExpectedProduction = -200;
            log.debug("Expecting min:{}, max: {}, current state {}",
                      minExpectedProduction,
                      maxExpectedProduction,
                      production);
            Assert.assertTrue("Production (" + production
                              + ") lower than minium production ("
                              + minExpectedProduction
                              + ")", minExpectedProduction <= production);
            Assert.assertTrue("Production (" + production
                              + ") higher dan maximum production ("
                              + maxExpectedProduction
                              + ")", maxExpectedProduction >= production);
        }
    }

    public void testSunnyWeather() throws Exception {
        OtherEndPVPanelApp otherEnd = create(1, 0.0, 200.0, 1500.0);

        pvSimulation.setWeather(Weather.sun);
        ignoreMeasures(otherEnd, 10); // ignore some measures, to be sure that the weather is set correctly
        for (int i = 0; i < 100; i++) {
            double production = getConsumptionMeasure(otherEnd);
            double minExpectedProduction = -1650;
            double maxExpectedProduction = -1500;
            log.debug("Expecting min:{}, max: {}, current state {}",
                      minExpectedProduction,
                      maxExpectedProduction,
                      production);
            Assert.assertTrue("Production (" + production
                              + ") lower than minium production ("
                              + minExpectedProduction
                              + ")", minExpectedProduction <= production);
            Assert.assertTrue("Production (" + production
                              + ") higher dan maximum production ("
                              + maxExpectedProduction
                              + ")", maxExpectedProduction >= production);
        }
    }

    public void testRandomFactor() throws Exception {
        OtherEndPVPanelApp otherEnd = create(1, 0.0, 200.0, 1500.0);
        pvSimulation.setWeather(Weather.clouds);
        ignoreMeasures(otherEnd, 10); // ignore some measures, to be sure that the weather is set correctly
        expectedRandomValues(otherEnd, -400.0, -200.0);

        pvSimulation.setWeather(Weather.sun);
        ignoreMeasures(otherEnd, 10); // ignore some measures, to be sure that the weather is set correctly
        expectedRandomValues(otherEnd, -1650.0, -1500.0);
    }

    public void
            expectedRandomValues(OtherEndPVPanelApp otherEnd, double minExpectedProduction, double maxExpectedProduction) throws InterruptedException {
        Set<Double> productions = new HashSet<Double>();
        int numberOfTests = 100;
        for (int i = 0; i < numberOfTests; i++) {
            double production = getConsumptionMeasure(otherEnd);
            log.info("production received: {}", production);

            Assert.assertTrue("Production (" + production
                              + ") lower than minium production ("
                              + minExpectedProduction
                              + ")", minExpectedProduction <= production);
            Assert.assertTrue("Production (" + production
                              + ") higher dan maximum production ("
                              + maxExpectedProduction
                              + ")", maxExpectedProduction >= production);
            productions.add(production);

        }
        // we assert that at leest 90% of all production numbers are different.. (because there is a random factor in
        // use)
        Assert.assertTrue(productions.size() >= 0.9 * numberOfTests);
    }

    private double getConsumptionMeasure(OtherEndPVPanelApp otherEnd) throws InterruptedException {

        UncontrolledUpdate uncontrolledUpdate = otherEnd.getUncontrolledUpdate();
        assertNotNull(uncontrolledUpdate);
        assertNotNull(uncontrolledUpdate.getValidFrom());
        assertNotNull(uncontrolledUpdate.getTimestamp());
        assertTrue(UncontrolledMeasurement.class.isAssignableFrom(uncontrolledUpdate.getClass()));
        UncontrolledMeasurement measurement = (UncontrolledMeasurement) uncontrolledUpdate;
        CommodityMeasurables measure = measurement.getMeasurable();
        return measure.get(Commodity.ELECTRICITY).doubleValue(SI.WATT);

    }

    private void ignoreMeasures(OtherEndPVPanelApp otherEnd, int numberOfMeasureToIgnore) throws InterruptedException {
        for (int i = 0; i < numberOfMeasureToIgnore; i++) {
            otherEnd.getUncontrolledUpdate();
        }
    }

}
