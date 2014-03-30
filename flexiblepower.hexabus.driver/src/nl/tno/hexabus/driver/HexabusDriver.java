package nl.tno.hexabus.driver;

import java.nio.ByteBuffer;
import java.util.Collection;

import nl.tno.hexabus.api.HexabusControlParameters;
import nl.tno.hexabus.api.HexabusState;
import nl.tno.hexabus.protocol.Data;
import nl.tno.hexabus.protocol.Data.Bool;
import nl.tno.hexabus.protocol.Data.UInt32;
import nl.tno.hexabus.protocol.Device;
import nl.tno.hexabus.protocol.Device.Endpoint;
import nl.tno.hexabus.protocol.PacketCodec.HexDumper;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.flexiblepower.ral.ResourceDriver;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.time.TimeService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class HexabusDriver extends AbstractResourceDriver<HexabusState, HexabusControlParameters> {
    // private static final Logger log = LoggerFactory.getLogger(HexabusDriver.class);

    private static boolean isSwitch(Endpoint<?> endpoint) {
        return endpoint.getDescription().contains("Switch") && endpoint.getType() == Data.Bool.class;
    }

    private static boolean isPower(Endpoint<?> endpoint) {
        return endpoint.getDescription().contains("Power") && endpoint.getType() == Data.UInt32.class;
    }

    private static final class HexabusStateImpl implements HexabusState {
        private final boolean connected, switchedOn;
        private final long currentLoad;

        HexabusStateImpl(boolean connected, boolean switchedOn, long currentLoad) {
            this.connected = connected;
            this.switchedOn = switchedOn;
            this.currentLoad = currentLoad;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public boolean isSwitchedOn() {
            return switchedOn;
        }

        @Override
        public long getCurrentLoad() {
            return currentLoad;
        }
    }

    private final Device device;
    private final TimeService timeService;
    private final ServiceRegistration<?> serviceRegistration;

    public HexabusDriver(BundleContext bundleContext, Device device, TimeService timeService) {
        this.device = device;
        this.timeService = timeService;

        device.setListener(new Device.Listener() {
            @Override
            public void updated(Endpoint<?> endpoint, final Data data) {
                if (isPower(endpoint)) {
                    final boolean switchedOn = HexabusDriver.this.isSwitchedOn();
                    publish(new Observation<HexabusState>(HexabusDriver.this.timeService.getTime(),
                                                          new HexabusStateImpl(true, switchedOn, (Long) data.getValue())));
                } else if (isSwitch(endpoint)) {
                    final long currentPower = HexabusDriver.this.getCurrentPower();
                    publish(new Observation<HexabusState>(HexabusDriver.this.timeService.getTime(),
                                                          new HexabusStateImpl(true,
                                                                               (Boolean) data.getValue(),
                                                                               currentPower)));
                }
            }
        });

        byte[] addressBytes = device.getAddress().getAddress();
        String deviceName = "hexabus-" + new HexDumper(ByteBuffer.wrap(addressBytes, addressBytes.length - 6, 6)).toString();

        serviceRegistration = new ObservationProviderRegistrationHelper(this).observationOf(deviceName)
                                                                             .observedBy(getClass().getName())
                                                                             .observationType(HexabusState.class)
                                                                             .setProperty("resourceId", deviceName)
                                                                             .register(ResourceDriver.class,
                                                                                       HexabusDriver.class);
    }

    public void unregister() {
        serviceRegistration.unregister();
    }

    @Override
    public void setControlParameters(HexabusControlParameters resourceControlParameters) {
        Endpoint<Data.Bool> switchEndpoint = detectSwitch();

        Data.Bool current = switchEndpoint.queryAndWait();
        if (current.get() ^ resourceControlParameters.isSwitchedOn()) {
            device.write(new Data.Bool(switchEndpoint.getEid(), resourceControlParameters.isSwitchedOn()));
        }
    }

    @SuppressWarnings("unchecked")
    private Endpoint<Data.Bool> detectSwitch() {
        Collection<Endpoint<?>> endpoints = device.getEndpoints();
        for (Endpoint<?> endpoint : endpoints) {
            if (isSwitch(endpoint)) {
                return (Endpoint<Bool>) endpoint;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Endpoint<Data.UInt32> detectPower() {
        Collection<Endpoint<?>> endpoints = device.getEndpoints();
        for (Endpoint<?> endpoint : endpoints) {
            if (isPower(endpoint)) {
                return (Endpoint<Data.UInt32>) endpoint;
            }
        }
        return null;
    }

    public long getCurrentPower() {
        Endpoint<UInt32> power = detectPower();
        if (power != null) {
            Data.UInt32 data = power.getData();
            if (data != null) {
                return data.get();
            }
        }
        return 0;
    }

    public boolean isSwitchedOn() {
        Endpoint<Bool> power = detectSwitch();
        if (power != null) {
            Data.Bool data = power.getData();
            if (data != null) {
                return data.get();
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return device.toString();
    }
}
