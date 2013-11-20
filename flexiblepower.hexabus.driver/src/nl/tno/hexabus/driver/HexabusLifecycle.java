package nl.tno.hexabus.driver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import de.fraunhofer.itwm.hexabus.Hexabus;
import de.fraunhofer.itwm.hexabus.Hexabus.PacketType;
import de.fraunhofer.itwm.hexabus.HexabusInfoPacket;
import de.fraunhofer.itwm.hexabus.HexabusPacket;
import de.fraunhofer.itwm.hexabus.NoResponseException;

@Component(immediate = true, provide = HexabusLifecycle.class)
public class HexabusLifecycle implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(HexabusLifecycle.class);

    private static final InetAddress MCAST_ADDR;
    static {
        try {
            MCAST_ADDR = InetAddress.getByName("ff02::1");
        } catch (UnknownHostException ex) {
            throw new Error("Should not be possible", ex);
        }
    }

    private final Map<InetAddress, HexabusDriver> drivers;
    private final AtomicBoolean running;
    private final Collection<HexabusLifecycleListener> listeners;

    public HexabusLifecycle() {
        drivers = Collections.synchronizedMap(new HashMap<InetAddress, HexabusDriver>());
        running = new AtomicBoolean(true);
        listeners = new CopyOnWriteArrayList<HexabusLifecycleListener>();
    }

    private ScheduledExecutorService scheduler;

    @Reference
    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    private BundleContext bundleContext;

    @Activate
    public void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
        running.set(true);
        scheduler.execute(this);
    }

    @Deactivate
    public void deactivate() {
        running.set(false);
    }

    private MulticastSocket ms;
    private int retry;
    private Hexabus hexabus;
    private ServiceRegistration<Hexabus> hexabusServiceRegistration;

    private synchronized Hexabus getHexabus() {
        if (ms == null || ms.isClosed()) {
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                NetworkInterface iface = null;
                while (interfaces.hasMoreElements()) {
                    iface = interfaces.nextElement();
                    if (iface.getDisplayName().contains("HEXABUS")) {
                        logger.debug("Using interface {}", iface);
                        break;
                    }
                    iface = null;
                }

                ms = new MulticastSocket(61616);
                if (iface != null) {
                    ms.setNetworkInterface(iface);
                } else {
                    logger.warn("No HEXABUS interface found! Trying to work on all interfaces");
                }
                ms.setBroadcast(true);
                ms.joinGroup(MCAST_ADDR);

                retry = 5;
                logger.debug("Opened the MulticastSocket on {}", ms.getLocalSocketAddress());

                hexabus = new Hexabus(ms);
                hexabusServiceRegistration = bundleContext.registerService(Hexabus.class, hexabus, null);
            } catch (IOException ex) {
                logger.warn("Error while opening multicast socket, trying again in " + retry + " seconds", ex);
                try {
                    Thread.sleep(retry * 1000);
                } catch (InterruptedException e) {
                }
                retry *= 2;
            }
        }

        return hexabus;
    }

    private synchronized void closeSocket() {
        if (hexabusServiceRegistration != null) {
            hexabusServiceRegistration.unregister();
            hexabusServiceRegistration = null;
        }

        if (ms != null) {
            try {
                ms.leaveGroup(MCAST_ADDR);
                ms.close();
            } catch (IOException ex) {
                logger.error("I/O Error", ex);
            }
        }
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                HexabusPacket packet;
                try {
                    packet = getHexabus().receivePacket();
                } catch (NoResponseException ex) {
                    continue;
                } catch (IOException ex) {
                    logger.warn("I/O error with the multicast socket", ex);
                    closeSocket();
                    continue;
                }
                logger.trace("Mulcicast packet received: {}", packet);

                InetAddress source = packet.getSourceAddress();
                if (!drivers.containsKey(source)) {
                    logger.debug("New device detected {}", source);
                    drivers.put(source, null);

                    for (HexabusLifecycleListener l : listeners) {
                        l.newHexabusDetected(this, source);
                    }
                }

                HexabusDriver driver = drivers.get(source);
                if (packet.getPacketType() == PacketType.INFO) {
                    if (driver != null) {
                        HexabusInfoPacket infoPacket = (HexabusInfoPacket) packet;
                        if (infoPacket.getEid() == 2) {
                            driver.update(infoPacket);
                        }
                    }
                }
            } catch (IOException ex) {
                logger.warn("I/O error with the multicast socket", ex);
                closeSocket();
            }
        }

        closeSocket();
        logger.debug("shutdown");
    }

    public void switchAllTo(boolean on) throws IOException {
        synchronized (drivers) {
            for (HexabusDriver driver : drivers.values()) {
                driver.switchTo(on);
            }
        }
    }

    public void addListener(HexabusLifecycleListener listener) {
        listeners.add(listener);
    }

    public void removedListener(HexabusLifecycleListener listener) {
        listeners.remove(listener);
    }

    public Iterable<HexabusDriver> getDrivers() {
        synchronized (drivers) {
            Set<HexabusDriver> result = new HashSet<HexabusDriver>(drivers.size() * 2);
            for (HexabusDriver driver : drivers.values()) {
                if (driver != null) {
                    result.add(driver);
                }
            }
            return result;
        }
    }

    public Iterable<InetAddress> getNewAddresses() {
        synchronized (drivers) {
            Set<InetAddress> result = new HashSet<InetAddress>(drivers.size() * 2);
            for (Entry<InetAddress, HexabusDriver> entry : drivers.entrySet()) {
                if (entry.getValue() == null) {
                    result.add(entry.getKey());
                }
            }
            return result;
        }
    }
}
