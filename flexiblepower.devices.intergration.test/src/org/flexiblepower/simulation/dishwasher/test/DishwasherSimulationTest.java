package org.flexiblepower.simulation.dishwasher.test;

import java.io.IOException;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;

import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.miele.dishwasher.manager.MieleDishwasherManager;
import org.flexiblepower.simulation.dishwasher.DishwasherSimulation;
import org.flexiblepower.simulation.test.SimulationTest;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.util.tracker.ServiceTracker;

public class DishwasherSimulationTest extends SimulationTest {
    private ServiceTracker<Endpoint, Endpoint> dishwasherSimulationTracker;
    private ServiceTracker<Endpoint, Endpoint> dishwasherManagerTracker;

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

    }

    private volatile Configuration simConfig;
    private volatile ServiceRegistration<Endpoint> otherEndRegistration;
    private volatile DishwasherSimulation dishwasherSimulation;
    private Configuration managerConfig;
    private MieleDishwasherManager dishwasherManager;

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

        OtherEndEnergyApp otherEnd = new OtherEndEnergyApp();
        otherEndRegistration = bundleContext.registerService(Endpoint.class, otherEnd, null);

        connectionManager.autoConnect();

        simulation.startSimulation(new Date(), 5);

        // PowerState initialState = otherEnd.getState();
        // assertEquals(selfDischargePower, initialState.getSelfDischargeSpeed().doubleValue(SI.WATT), 0.01);
        // TODO add assertions.... (What can we assert here?)

        return otherEnd;
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
    }

    public void testAutoconnect() throws Exception {
        OtherEndEnergyApp otherEnd = create(1, true, "2014-09-10 15:30", "2014-09-11 15:30", "Aan", true);
        assertNotNull(otherEnd.getConnection());
    }

}
