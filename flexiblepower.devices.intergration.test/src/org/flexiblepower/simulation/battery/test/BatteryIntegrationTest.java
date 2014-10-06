package org.flexiblepower.simulation.battery.test;

import java.io.IOException;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.unit.SI;

import org.flexiblepower.battery.manager.BatteryManager;
import org.flexiblepower.efi.buffer.BufferRegistration;
import org.flexiblepower.efi.buffer.BufferStateUpdate;
import org.flexiblepower.efi.buffer.BufferSystemDescription;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.simulation.battery.BatterySimulation;
import org.flexiblepower.simulation.test.SimulationTest;
import org.flexiblepower.time.TimeService;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.component.annotations.Reference;
import org.osgi.util.tracker.ServiceTracker;

public class BatteryIntegrationTest extends SimulationTest {
    private ServiceTracker<Endpoint, Endpoint> batteryTracker;
    private ServiceTracker<Endpoint, Endpoint> bufferManagerTracker;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        batteryTracker = new ServiceTracker<Endpoint, Endpoint>(bundleContext,
                                                                bundleContext.createFilter("(testa=batterysim)"),
                                                                null);
        batteryTracker.open();

        bufferManagerTracker = new ServiceTracker<Endpoint, Endpoint>(bundleContext,
                                                                      bundleContext.createFilter("(testb=batterysim)"),
                                                                      null);
        bufferManagerTracker.open();

    }

    private volatile Configuration simConfig;
    private volatile ServiceRegistration<Endpoint> otherEndRegistration;
    private volatile BatterySimulation batterySimulation;
    private Configuration managerConfig;
    private BatteryManager batteryManager;

    private OtherEndBatteryEnergyApp create(long updateInterval,
                                            double totalCapacity,
                                            double initialStateOfCharge,
                                            long chargePower,
                                            long dischargePower,
                                            double chargeEfficiency,
                                            double dischargeEfficiency,
                                            long selfDischargePower

            ) throws Exception {
        // config battery simulation
        simConfig = configAdmin.createFactoryConfiguration("org.flexiblepower.simulation.battery.BatterySimulation",
                                                           null);
        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put("updateInterval", updateInterval);
        properties.put("totalCapacity", totalCapacity);
        properties.put("initialStateOfCharge", initialStateOfCharge);
        properties.put("chargePower", chargePower);
        properties.put("dischargePower", dischargePower);
        properties.put("chargeEfficiency", chargeEfficiency);
        properties.put("dischargeEfficiency", dischargeEfficiency);
        properties.put("selfDischargePower", selfDischargePower);
        properties.put("testa", "batterysim");
        simConfig.update(properties);
        batterySimulation = (BatterySimulation) batteryTracker.waitForService(5000);
        assertNotNull(batterySimulation);

        // config battery manager
        managerConfig = configAdmin.createFactoryConfiguration("org.flexiblepower.battery.manager.BatteryManager",
                                                               null);
        Dictionary<String, Object> managerProperties = new Hashtable<String, Object>();
        managerProperties.put("resourceId", "battery");
        managerProperties.put("testb", "batterysim");
        managerConfig.update(managerProperties);
        batteryManager = (BatteryManager) bufferManagerTracker.waitForService(5000);
        assertNotNull(batteryManager);

        // set up the other end = energy app simulation
        OtherEndBatteryEnergyApp otherEnd = new OtherEndBatteryEnergyApp();
        otherEndRegistration = bundleContext.registerService(Endpoint.class, otherEnd, null);
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

        simulation.startSimulation(new Date(), 10);

        return otherEnd;
    }

    @Override
    protected void tearDown() throws Exception {
        destroy();
        batteryTracker.close();
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
        OtherEndBatteryEnergyApp otherEnd = create(5L, 1, 0.7, 1500L, 1500L, 0.9, 0.9, 50L);
        assertNotNull(otherEnd.getConnection());
    }

    public void testRegistration() throws Exception {
        OtherEndBatteryEnergyApp otherEnd = create(5L, 1, 0.7, 1500L, 1500L, 0.9, 0.9, 50L);
        BufferRegistration<?> bufferRegistration = otherEnd.getBufferRegistration();
        assertNotNull(bufferRegistration);
        Measurable<Duration> allocationDelay = bufferRegistration.getAllocationDelay();
        assertEquals(allocationDelay, Measure.valueOf(0, SI.SECOND));
        BufferSystemDescription bufferSystemDescription = otherEnd.getBufferSystemDescription();
        assertNotNull(bufferSystemDescription);
        BufferStateUpdate<?> bufferStateUpdate = otherEnd.getBufferStateUpdate();
        assertNotNull(bufferStateUpdate);
    }

    public void testAllocation() throws Exception {
        OtherEndBatteryEnergyApp otherEnd = create(1L, 1, 0.7, 1500L, 1500L, 0.9, 0.9, 50L);
        BufferRegistration<?> bufferRegistration = otherEnd.getBufferRegistration();
        assertNotNull(bufferRegistration);
        Measurable<Duration> allocationDelay = bufferRegistration.getAllocationDelay();
        assertEquals(allocationDelay, Measure.valueOf(0, SI.SECOND));
        BufferSystemDescription bufferSystemDescription = otherEnd.getBufferSystemDescription();
        assertNotNull(bufferSystemDescription);
        BufferStateUpdate<?> bufferStateUpdate = otherEnd.getBufferStateUpdate();
        assertNotNull(bufferStateUpdate);

        otherEnd.sendAllocation(bufferSystemDescription, bufferStateUpdate);

        BufferStateUpdate<?> bufferStateUpdate2 = otherEnd.getBufferStateUpdate();
        assertNotNull(bufferStateUpdate2);

        Thread.sleep(5000); // let the battery simulation for 5 sec * (simulationspeed = 10)
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

}
