package org.flexiblepower.simulation.pvpanel.test;

import java.util.Dictionary;
import java.util.Hashtable;

import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.simulation.pvpanel.PVSimulation;
import org.flexiblepower.simulation.pvpanel.Weather;
import org.flexiblepower.simulation.test.SimulationTest;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.util.tracker.ServiceTracker;

public class PVSimulationTest extends SimulationTest {
    private ServiceTracker<Endpoint, Endpoint> pvpanelSimulationTracker;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        pvpanelSimulationTracker = new ServiceTracker<Endpoint, Endpoint>(bundleContext,
                                                                          bundleContext.createFilter("(test=pvsim)"),
                                                                          null);
        pvpanelSimulationTracker.open();
    }

    private volatile Configuration config;
    private volatile ServiceRegistration<Endpoint> otherEndRegistration;

    private PVSimulation pvSimulation = null;

    private OtherEndPVPanelManager create(int updateDelay,
                                          double powerWhenStandBy,
                                          double powerWhenCloudy,
                                          double powerWhenSunny) throws Exception {
        config = configAdmin.createFactoryConfiguration("org.flexiblepower.simulation.pvpanel.PVSimulation",
                                                        null);
        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put("updateDelay", updateDelay);
        properties.put("powerWhenStandBy", powerWhenStandBy);
        properties.put("powerWhenCloudy", powerWhenCloudy);
        properties.put("powerWhenSunny", powerWhenSunny);
        properties.put("test", "pvsim");
        config.update(properties);

        pvSimulation = (PVSimulation) pvpanelSimulationTracker.waitForService(1000);
        assertNotNull(pvSimulation);

        OtherEndPVPanelManager otherEnd = new OtherEndPVPanelManager();
        otherEndRegistration = bundleContext.registerService(Endpoint.class, otherEnd, null);

        connectAndStartSimulation(2);

        return otherEnd;
    }

    @Override
    protected void tearDown() throws Exception {
        simulation.stopSimulation();

        if (otherEndRegistration != null) {
            otherEndRegistration.unregister();
            otherEndRegistration = null;
        }
        if (config != null) {
            config.delete();
            config = null;
        }

        pvpanelSimulationTracker.close();

        super.tearDown();
    }

    public void testMoonWeather() throws Exception {
        OtherEndPVPanelManager otherEnd = create(1, 0.0, 200.0, 1500.0);
        pvSimulation.setWeather(Weather.moon);
        ignoreMeasures(otherEnd, 10); // ignore some measures, to be sure that the weather is set correctly
        for (int i = 0; i < 100; i++) {
            otherEnd.expectedState(0.0, 0.0);
        }
    }

    public void testCloudyWeather() throws Exception {
        OtherEndPVPanelManager otherEnd = create(1, 0.0, 200.0, 1500.0);
        pvSimulation.setWeather(Weather.clouds);
        ignoreMeasures(otherEnd, 10); // ignore some measures, to be sure that the weather is set correctly
        for (int i = 0; i < 100; i++) {
            otherEnd.expectedState(-400.0, -200.0);
        }
    }

    public void testSunnyWeather() throws Exception {
        OtherEndPVPanelManager otherEnd = create(1, 0.0, 200.0, 1500.0);
        pvSimulation.setWeather(Weather.sun);
        ignoreMeasures(otherEnd, 10); // ignore some measures, to be sure that the weather is set correctly
        for (int i = 0; i < 100; i++) {
            otherEnd.expectedState(-1650.0, -1500.0);
        }
    }

    public void testRandomFactor() throws Exception {
        OtherEndPVPanelManager otherEnd = create(1, 0.0, 200.0, 1500.0);
        pvSimulation.setWeather(Weather.clouds);
        ignoreMeasures(otherEnd, 10); // ignore some measures, to be sure that the weather is set correctly
        otherEnd.expectedRandomValues(-400.0, -200.0);

        pvSimulation.setWeather(Weather.sun);
        ignoreMeasures(otherEnd, 10); // ignore some measures, to be sure that the weather is set correctly
        otherEnd.expectedRandomValues(-1650.0, -1500.0);

    }

    private void
            ignoreMeasures(OtherEndPVPanelManager otherEnd, int numberOfMeasureToIgnore) throws InterruptedException {
        for (int i = 0; i < numberOfMeasureToIgnore; i++) {
            otherEnd.getState();
        }
    }

}
