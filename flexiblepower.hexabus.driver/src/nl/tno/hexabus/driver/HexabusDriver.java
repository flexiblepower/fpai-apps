package nl.tno.hexabus.driver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import nl.tno.hexabus.api.HexabusControlParameters;
import nl.tno.hexabus.api.HexabusState;
import nl.tno.hexabus.driver.HexabusDriver.Config;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.flexiblepower.ral.ResourceDriver;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.time.TimeService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;
import de.fraunhofer.itwm.hexabus.Hexabus;
import de.fraunhofer.itwm.hexabus.Hexabus.DataType;
import de.fraunhofer.itwm.hexabus.HexabusDevice;
import de.fraunhofer.itwm.hexabus.HexabusEndpoint;
import de.fraunhofer.itwm.hexabus.HexabusInfoPacket;

@Component(designateFactory = Config.class, provide = {}, immediate = true)
public class HexabusDriver extends AbstractResourceDriver<HexabusState, HexabusControlParameters> {
    private static final Logger log = LoggerFactory.getLogger(HexabusDriver.class);

    public interface Config {
        @Meta.AD(description = "The IPv6 address of the Hexabus device (found on the label)",
                 deflt = "fe80::50:c4ff:fe04:819a")
        String inet_address();

        @Meta.AD(description = "The port number on which the device can be found (usually 61616)", deflt = "61616")
        int port();

        @Meta.AD(description = "The resourceId as needed for the FPAI framework to be wired to an energy app",
                 deflt = "hexabus1")
        String resource_id();
    }

    static class State implements HexabusState {
        private final boolean switchedOn;
        private final long currentLoad;

        public State(boolean switchedOn, long currentLoad) {
            this.switchedOn = switchedOn;
            this.currentLoad = currentLoad;
        }

        @Override
        public boolean isSwitchedOn() {
            return switchedOn;
        }

        @Override
        public long getCurrentLoad() {
            return currentLoad;
        }

        @Override
        public boolean isConnected() {
            return true;
        }
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    private ScheduledExecutorService scheduler;

    @Reference
    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    private Hexabus hexabus;

    @Reference
    public void setHexabus(Hexabus hexabus) {
        this.hexabus = hexabus;
    }

    private Logger logger;
    private HexabusDevice dev;
    private HexabusEndpoint loadEndpoint, switchEndpoint;
    private ScheduledFuture<?> schedule;

    @Activate
    public void activate(BundleContext context, Map<String, ?> properties) {
        Config config = Configurable.createConfigurable(Config.class, properties);
        try {
            logger = LoggerFactory.getLogger(HexabusDriver.class + "." + config.resource_id());
            InetAddress address = InetAddress.getByName(config.inet_address());
            dev = new HexabusDevice(hexabus, address, config.port());
            logger.debug("Started device {}, fetching endpoints", address);
            logger.debug("Started device {}, got endpoints {}", address, dev.fetchEndpoints());

            switchEndpoint = dev.getByEid(1);
            if (switchEndpoint.getDataType() != DataType.BOOL) {
                logger.warn("Expected a switch endpoint at address 1, but it wasn't");
                switchEndpoint = null;
            }

            loadEndpoint = dev.getByEid(2);
            if (loadEndpoint.getDataType() != DataType.UINT32) {
                logger.warn("Expected a switch endpoint at address 2, but it wasn't");
                loadEndpoint = null;
            }

            schedule = scheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    try {
                        update(null);
                    } catch (IOException e) {
                        logger.warn("Error during update of HEXABUS: " + e.getMessage(), e);
                    }
                }
            }, 0, 10, TimeUnit.SECONDS);

            serviceRegistration = new ObservationProviderRegistrationHelper(this).observationType(State.class)
                                                                                 .register(ResourceDriver.class,
                                                                                           HexabusDriver.class);
        } catch (UnknownHostException e) {
            logger.error("The given address is not a valid address: " + e.getMessage());
            throw new IllegalArgumentException("The given address is not a valid address: " + e.getMessage());
        } catch (IOException e) {
            logger.error("Could not read the remote endpoints: " + e.getMessage());
            throw new IllegalStateException("Could not read the remote endpoints: " + e.getMessage(), e);
        }
    }

    @Deactivate
    public void deactivate() {
        serviceRegistration.unregister();
        schedule.cancel(false);
    }

    @Override
    public void setControlParameters(HexabusControlParameters resourceControlParameters) {
        try {
            switchEndpoint.writeEndpoint(resourceControlParameters.isSwitchedOn());
        } catch (IOException ex) {
            log.error("Couldn't switch to [" + resourceControlParameters.isSwitchedOn() + "], I/O Error", ex);
        }
    }

    private long currentPower;
    private boolean switchedOn;
    private ServiceRegistration<?> serviceRegistration;

    public long getCurrentPower() {
        return currentPower;
    }

    public boolean isSwitchedOn() {
        return switchedOn;
    }

    public void update(HexabusInfoPacket infoPacket) throws IOException {
        if (loadEndpoint != null) {
            if (infoPacket != null && infoPacket.getEid() == loadEndpoint.getEid()) {
                currentPower = infoPacket.getUint32();
            } else {
                currentPower = loadEndpoint.queryUint32Endpoint();
            }
        }
        if (switchEndpoint != null) {
            if (infoPacket != null && infoPacket.getEid() == switchEndpoint.getEid()) {
                switchedOn = infoPacket.getBool();
            } else {
                switchedOn = switchEndpoint.queryBoolEndpoint();
            }
        }

        logger.debug("Updated power to {}, switchedOn to {}", currentPower, switchedOn);
        publish(new Observation<HexabusState>(timeService.getTime(), new State(switchedOn, currentPower)));
    }

    @Override
    public String toString() {
        return dev.getInetAddress() + " is switched "
               + (switchedOn ? "on" : "off")
               + " and uses "
               + currentPower
               + " Watts";
    }

    public void switchTo(boolean on) throws IOException {
        logger.debug("Switching {} to {}", dev.getInetAddress(), on ? "on" : "off");
        dev.getByEid(1).writeEndpoint(on);
    }

    public InetAddress getAddress() {
        return dev.getInetAddress();
    }
}
