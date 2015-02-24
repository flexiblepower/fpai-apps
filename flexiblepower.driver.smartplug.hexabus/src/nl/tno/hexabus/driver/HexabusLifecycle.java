package nl.tno.hexabus.driver;

import nl.tno.hexabus.protocol.Channel;
import nl.tno.hexabus.protocol.Channel.Communicator;
import nl.tno.hexabus.protocol.Device;
import nl.tno.hexabus.protocol.Packet;

import org.osgi.framework.BundleContext;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;

@Component(immediate = true, provide = HexabusLifecycle.class)
public class HexabusLifecycle implements Communicator {
    // private static final Logger log = LoggerFactory.getLogger(HexabusLifecycle.class);

    public HexabusLifecycle() {
    }

    private BundleContext bundleContext;
    private Channel channel;

    @Activate
    public void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext;

        channel = new Channel(this);
        channel.open();
    }

    @Deactivate
    public void deactivate() {
        channel.close();
    }

    @Override
    public boolean handlePacket(Packet packet, Channel channel) {
        Device device = new Device(packet.getAddress(), packet.getPort());
        channel.registerListener(packet.getAddress(), device);
        device.handlePacket(packet, channel);

        new HexabusDriver(bundleContext, device);

        return false;
    }

    @Override
    public Packet getNextRequest() {
        return null;
    }
}
