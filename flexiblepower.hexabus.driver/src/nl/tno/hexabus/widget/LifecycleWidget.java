package nl.tno.hexabus.widget;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

import nl.tno.hexabus.driver.HexabusDriver;
import nl.tno.hexabus.driver.HexabusLifecycle;

import org.flexiblepower.ui.Widget;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;

@Component(properties = { "widget.type=full", "widget.name=hexabus" })
public class LifecycleWidget implements Widget {
    private static final Logger log = LoggerFactory.getLogger(LifecycleWidget.class);

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

    private ConfigurationAdmin configAdmin;

    @Reference
    public void setConfigAdmin(ConfigurationAdmin configAdmin) {
        this.configAdmin = configAdmin;
    }

    private HexabusLifecycle lifecycle;

    @Reference
    public void setLifecycle(HexabusLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    public Collection<String> getNewAdresses() {
        List<String> result = new ArrayList<String>();
        for (InetAddress address : lifecycle.getNewAddresses()) {
            result.add(address.getHostAddress());
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
        try {
            Configuration config = configAdmin.createFactoryConfiguration(HexabusDriver.class.getName());
            Dictionary<String, Object> properties = new Hashtable<String, Object>();
            properties.put("inet.address", desc.getAddress());
            properties.put("port", 61616);
            properties.put("resource.id", desc.getName());
            config.update(properties);
        } catch (IOException ex) {
            log.error("Couldn't create config for the HexabusDriver, I/O error", ex);
        }
    }

    public void stopDriver(String name) {
        try {
            Configuration[] configs = configAdmin.listConfigurations("(&(service.factoryPid=" + HexabusDriver.class.getName()
                                                                     + ")(resourceId="
                                                                     + name
                                                                     + "))");
            if (configs != null && configs.length == 1) {
                configs[0].delete();
            }
        } catch (IOException ex) {
            log.error("Couldn't delete config for the HexabusDriver, I/O error", ex);
        } catch (InvalidSyntaxException ex) {
            log.warn("Couldn't delete config for the HexabusDriver, invalid name", ex);
        }
    }

    @Override
    public String getTitle(Locale locale) {
        return "Hexabus";
    }
}
