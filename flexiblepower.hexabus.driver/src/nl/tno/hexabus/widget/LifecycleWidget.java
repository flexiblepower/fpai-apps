package nl.tno.hexabus.widget;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import nl.tno.hexabus.driver.HexabusDriver;
import nl.tno.hexabus.driver.HexabusLifecycle;

import org.flexiblepower.ui.Widget;

import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;

@Component(properties = { "widget.type=full", "widget.name=hexabus" })
public class LifecycleWidget implements Widget {
    public static class DriverDescription {
        private String address, name;

        public void setName(String name) {
            this.name = name;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getAddress() {
            return address;
        }

        public String getName() {
            return name;
        }
    }

    private HexabusLifecycle lifecycle;

    @Reference
    public void setLifecycle(HexabusLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    public Collection<String> getNewAdresses() {
        List<String> result = new ArrayList<String>();
        for (InetAddress address : lifecycle.getNewAddresses()) {
            result.add(address.toString());
        }
        return result;
    }

    public Collection<String> getDevices() {
        List<String> result = new ArrayList<String>();
        for (HexabusDriver driver : lifecycle.getDrivers()) {
            result.add(driver.toString());
        }
        return result;
    }

    public void startDriver(DriverDescription desc) {

    }

    @Override
    public String getTitle(Locale locale) {
        return "Hexabus";
    }
}
