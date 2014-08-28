package org.flexiblepower.simulation.uncontrolled.manager.test;

import java.io.IOException;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;

import org.flexiblepower.efi.uncontrolled.UncontrolledUpdate;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.simulation.pvpanel.PVSimulation;
import org.flexiblepower.simulation.pvpanel.test.OtherEndPVPanel;
import org.flexiblepower.simulation.test.SimulationTest;
import org.flexiblepower.uncontrolled.manager.UncontrolledManager;
import org.osgi.service.cm.Configuration;
import org.osgi.util.tracker.ServiceTracker;

@Port(name = "manager", accepts = UncontrolledUpdate.class)
public class UncontrolledManagerSimulationTest extends SimulationTest {
    private ServiceTracker<Endpoint, Endpoint> uncontrolledManagerTracker;
    private ServiceTracker<Endpoint, Endpoint> pvpanelSimulationTracker;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        pvpanelSimulationTracker = new ServiceTracker<Endpoint, Endpoint>(bundleContext,
                                                                          bundleContext.createFilter("(test=pvsim)"),
                                                                          null);
        pvpanelSimulationTracker.open();

        uncontrolledManagerTracker = new ServiceTracker<Endpoint, Endpoint>(bundleContext,
                                                                            bundleContext.createFilter("(test=uncontrolledManager)"),
                                                                            null);
        uncontrolledManagerTracker.open();
    }

    @Override
    protected void tearDown() throws Exception {
        destroy();
        uncontrolledManagerTracker.close();
        pvpanelSimulationTracker.close();
        super.tearDown();
    }

    private void destroy() throws IOException {
        simulation.stopSimulation();
        //
        // if (otherEndRegistration != null) {
        // otherEndRegistration.unregister();
        // otherEndRegistration = null;
        // }
        // if (config != null) {
        // config.delete();
        // config = null;
        // }
    }

    private volatile Configuration pvPanelConfig;
    private volatile Configuration uncontrolledManagerConfig;
    private PVSimulation pvSimulation = null;
    private UncontrolledManager uncontrolledManager = null;

    private OtherEndPVPanel create(int updateFrequency,
                                   double powerWhenStandBy,
                                   double powerWhenCloudy,
                                   double powerWhenSunny) throws Exception {
        pvPanelConfig = configAdmin.createFactoryConfiguration("org.flexiblepower.simulation.pvpanel.PVSimulation",
                                                               null);
        Dictionary<String, Object> pvPanelProperties = new Hashtable<String, Object>();
        pvPanelProperties.put("updateFrequency", updateFrequency);
        pvPanelProperties.put("powerWhenStandBy", powerWhenStandBy);
        pvPanelProperties.put("powerWhenCloudy", powerWhenCloudy);
        pvPanelProperties.put("powerWhenSunny", powerWhenSunny);
        pvPanelProperties.put("test", "pvsim");
        pvPanelConfig.update(pvPanelProperties);

        pvSimulation = (PVSimulation) pvpanelSimulationTracker.waitForService(1000);

        assertNotNull(pvSimulation);

        uncontrolledManagerConfig = configAdmin.createFactoryConfiguration("org.flexiblepower.simulation.pvpanel.PVSimulation",
                                                                           null);
        Dictionary<String, Object> uncontrolledManagerProperties = new Hashtable<String, Object>();
        uncontrolledManagerProperties.put("test", "uncontrolledManager");
        uncontrolledManagerConfig.update(uncontrolledManagerProperties);

        uncontrolledManager = (UncontrolledManager) uncontrolledManagerTracker.waitForService(1000);

        assertNotNull(uncontrolledManager);

        // OtherEndPVPanel otherEnd = new OtherEndPVPanel();
        // otherEndRegistration = bundleContext.registerService(Endpoint.class, otherEnd, null);

        connectionManager.autoConnect();

        simulation.startSimulation(new Date(), 50);

        // PowerState initialState = otherEnd.getState();
        // assertEquals(selfDischargePower, initialState.getSelfDischargeSpeed().doubleValue(SI.WATT), 0.01);
        // TODO add assertions.... (What can we assert here?)

        // return otherEnd;
        return null;
    }

    public void testCreate() throws Exception {

        create(1, 0.0, 200.0, 1500.0);

    }

}
