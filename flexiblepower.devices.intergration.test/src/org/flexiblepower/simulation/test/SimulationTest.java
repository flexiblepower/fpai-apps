package org.flexiblepower.simulation.test;

import java.util.Date;

import junit.framework.TestCase;

import org.flexiblepower.messaging.ConnectionManager;
import org.flexiblepower.simulation.Simulation;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SimulationTest extends TestCase {
    public static final Logger log = LoggerFactory.getLogger(SimulationTest.class);

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
        simulation = null;
        simulationTracker.close();
        connectionManager = null;
        connectionManagerTracker.close();
        configAdmin = null;
        configAdminTracker.close();

        super.tearDown();
    }

    protected void connectAndStartSimulation(int expectedNrOfEndpoints) throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            if (connectionManager.getEndpoints().size() < expectedNrOfEndpoints) {
                Thread.sleep(50);
            } else {
                break;
            }
        }
        assertEquals(expectedNrOfEndpoints, connectionManager.getEndpoints().size());

        connectionManager.autoConnect();
        // Always start at 1 january 2014 0:00 and make it as quick as possible
        simulation.startSimulation(new Date(1388534400000L), 10000);
    }
}
