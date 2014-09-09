package org.flexiblepower.simulation.test;

import junit.framework.TestCase;

import org.flexiblepower.messaging.ConnectionManager;
import org.flexiblepower.simulation.Simulation;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;

public abstract class SimulationTest extends TestCase {
    protected BundleContext bundleContext;

    private ServiceTracker<ConfigurationAdmin, ConfigurationAdmin> configAdminTracker;
    protected ConfigurationAdmin configAdmin;

    private ServiceTracker<ConnectionManager, ConnectionManager> connectionManagerTracker;
    protected ConnectionManager connectionManager;

    private ServiceTracker<Simulation, Simulation> simulationTracker;
    protected Simulation simulation;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();

        configAdminTracker = new ServiceTracker<ConfigurationAdmin, ConfigurationAdmin>(bundleContext,
                ConfigurationAdmin.class,
                null);
        configAdminTracker.open();
        configAdmin = configAdminTracker.waitForService(10000);
        assertNotNull(configAdmin);

        connectionManagerTracker = new ServiceTracker<ConnectionManager, ConnectionManager>(bundleContext,
                ConnectionManager.class,
                null);
        connectionManagerTracker.open();
        connectionManager = connectionManagerTracker.waitForService(10000);
        assertNotNull(connectionManager);

        simulationTracker = new ServiceTracker<Simulation, Simulation>(bundleContext,
                Simulation.class,
                null);
        simulationTracker.open();
        simulation = simulationTracker.waitForService(10000);
        assertNotNull(simulation);
    }

    @Override
    protected void tearDown() throws Exception {
        simulationTracker.close();
        connectionManagerTracker.close();
        configAdminTracker.close();
        super.tearDown();
    }
}
