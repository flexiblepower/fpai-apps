package org.flexiblepower.simulation.uncontrolled.manager.test;

import java.io.IOException;

import org.flexiblepower.efi.uncontrolled.UncontrolledUpdate;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.simulation.test.SimulationTest;
import org.osgi.util.tracker.ServiceTracker;

@Port(name = "manager", accepts = UncontrolledUpdate.class)
public class UncontrolledManagerSimulationTest extends SimulationTest {
    private ServiceTracker<Endpoint, Endpoint> uncontrolledManagerSimulationTracker;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        uncontrolledManagerSimulationTracker = new ServiceTracker<Endpoint, Endpoint>(bundleContext,
                                                                                      bundleContext.createFilter("(test=pvsim)"),
                                                                                      null);
        uncontrolledManagerSimulationTracker.open();
    }

    @Override
    protected void tearDown() throws Exception {
        destroy();
        uncontrolledManagerSimulationTracker.close();
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

}
