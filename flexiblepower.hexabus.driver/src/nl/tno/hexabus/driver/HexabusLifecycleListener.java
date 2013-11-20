package nl.tno.hexabus.driver;

import java.net.InetAddress;

public interface HexabusLifecycleListener {
    void newHexabusDetected(HexabusLifecycle lifecycle, InetAddress address);
}
