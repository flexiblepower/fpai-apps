package org.flexiblepower.simulation.pvpanel.test;

import java.io.IOException;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;

import junit.framework.TestCase;

import org.flexiblepower.messaging.ConnectionManager;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.ral.drivers.uncontrolled.PowerState;
import org.flexiblepower.simulation.Simulation;
import org.flexiblepower.simulation.pvpanel.PVSimulation;
import org.flexiblepower.simulation.pvpanel.Weather;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PVSimulationTest extends TestCase {
    private BundleContext bundleContext;
    private static final Logger log = LoggerFactory.getLogger(PVSimulationTest.class);

    private ServiceTracker<ConfigurationAdmin, ConfigurationAdmin> configAdminTracker;
    private ConfigurationAdmin configAdmin;

    private ServiceTracker<ConnectionManager, ConnectionManager> connectionManagerTracker;
    private ConnectionManager connectionManager;

    private ServiceTracker<Simulation, Simulation> simulationTracker;
    private Simulation simulation;

    private ServiceTracker<Endpoint, Endpoint> pvpanelSimulationTracker;

    @Override
    protected void setUp() throws Exception {
        bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();

        configAdminTracker = new ServiceTracker<ConfigurationAdmin, ConfigurationAdmin>(bundleContext,
                                                                                        ConfigurationAdmin.class,
                                                                                        null);
        configAdminTracker.open();
        configAdmin = configAdminTracker.waitForService(1000);

        connectionManagerTracker = new ServiceTracker<ConnectionManager, ConnectionManager>(bundleContext,
                                                                                            ConnectionManager.class,
                                                                                            null);
        connectionManagerTracker.open();
        connectionManager = connectionManagerTracker.waitForService(1000);

        simulationTracker = new ServiceTracker<Simulation, Simulation>(bundleContext,
                                                                       Simulation.class,
                                                                       null);
        simulationTracker.open();
        simulation = simulationTracker.waitForService(1000);

        pvpanelSimulationTracker = new ServiceTracker<Endpoint, Endpoint>(bundleContext,
                                                                          bundleContext.createFilter("(test=pvsim)"),
                                                                          null);
        pvpanelSimulationTracker.open();

    }

    private volatile Configuration config;
    private volatile ServiceRegistration<Endpoint> otherEndRegistration;

    private PVSimulation pvSimulation = null;

    private OtherEndPVPanel create(int updateFrequency,
                                   double powerWhenStandBy,
                                   double powerWhenCloudy,
                                   double powerWhenSunny) throws Exception {
        config = configAdmin.createFactoryConfiguration("org.flexiblepower.simulation.pvpanel.PVSimulation",
                                                        null);
        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put("updateFrequency", updateFrequency);
        properties.put("powerWhenStandBy", powerWhenStandBy);
        properties.put("powerWhenCloudy", powerWhenCloudy);
        properties.put("powerWhenSunny", powerWhenSunny);
        properties.put("test", "pvsim");
        config.update(properties);

        pvSimulation = (PVSimulation) pvpanelSimulationTracker.waitForService(1000);

        assertNotNull(pvSimulation);

        log.info("registering otherEnd");

        OtherEndPVPanel otherEnd = new OtherEndPVPanel();
        otherEndRegistration = bundleContext.registerService(Endpoint.class, otherEnd, null);

        log.info("Autoconnect starting");
        connectionManager.autoConnect();
        log.info("Autoconnect finished");

        simulation.startSimulation(new Date(), 5);

        PowerState initialState = otherEnd.getState();
        // assertEquals(selfDischargePower, initialState.getSelfDischargeSpeed().doubleValue(SI.WATT), 0.01);
        // TODO add assertions.... (What can we assert here?)

        return otherEnd;
    }

    @Override
    protected void tearDown() throws Exception {
        destroy();
        pvpanelSimulationTracker.close();
        simulationTracker.close();
        connectionManagerTracker.close();
        configAdminTracker.close();
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

    public void testMoonWeather() throws Exception {
        OtherEndPVPanel otherEnd = create(1, 0.0, 200.0, 1500.0);
        pvSimulation.setWeather(Weather.moon);
        for (int i = 0; i < 1; i++) {
            otherEnd.expectedState(0.0, 0.0);
        }
    }

    public void testCloudyWeather() throws Exception {
        OtherEndPVPanel otherEnd = create(1, 0.0, 200.0, 1500.0);
        pvSimulation.setWeather(Weather.clouds);
        for (int i = 0; i < 1; i++) {
            otherEnd.expectedState(-400.0, -200.0);
        }
    }

    public void testSunnyWeather() throws Exception {
        OtherEndPVPanel otherEnd = create(1, 0.0, 200.0, 1500.0);
        pvSimulation.setWeather(Weather.sun);
        for (int i = 0; i < 1; i++) {
            otherEnd.expectedState(-1650.0, -1500.0);
        }
    }

    public void testRandomFactor() throws Exception {
        OtherEndPVPanel otherEnd = create(1, 0.0, 200.0, 1500.0);

        pvSimulation.setWeather(Weather.clouds);
        for (int i = 0; i < 1; i++) {
            otherEnd.expectedDifferentState();
        }

        pvSimulation.setWeather(Weather.moon);
        otherEnd.expectedState(0, 0);
        for (int i = 0; i < 1; i++) {
            otherEnd.expectSameState();
        }

        pvSimulation.setWeather(Weather.sun);
        for (int i = 0; i < 1; i++) {
            otherEnd.expectedDifferentState();
        }

    }

}
