package nl.tno.hexabus.widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import nl.tno.hexabus.driver.HexabusDriver;

import org.flexiblepower.ui.Widget;

import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;

@Component
public class HexabusWidget implements Widget {
    private final Map<String, HexabusDriver> drivers = new HashMap<String, HexabusDriver>();

    @Reference(multiple = true, dynamic = true)
    public void addDriver(HexabusDriver driver, Map<String, Object> properties) {
        if (properties.containsKey("resource.id")) {
            String id = properties.get("resource.id").toString();
            drivers.put(id, driver);
        }
    }

    public void removeDriver(HexabusDriver driver, Map<String, Object> properties) {
        if (properties.containsKey("resource.id")) {
            String id = properties.get("resource.id").toString();
            drivers.remove(id);
        }
    }

    public static class HexabusInfo {
        private final String deviceName;
        private final long power;
        private final boolean switchedOn;

        public HexabusInfo(String deviceName, long power, boolean switchedOn) {
            this.deviceName = deviceName;
            this.power = power;
            this.switchedOn = switchedOn;
        }

        public String getDeviceName() {
            return deviceName;
        }

        public long getPower() {
            return power;
        }

        public boolean isSwitchedOn() {
            return switchedOn;
        }
    }

    public List<HexabusInfo> getInfo() {
        List<HexabusInfo> result = new ArrayList<HexabusWidget.HexabusInfo>();
        for (Entry<String, HexabusDriver> entry : drivers.entrySet()) {
            result.add(new HexabusInfo(entry.getKey(), entry.getValue().getCurrentPower(), entry.getValue()
                                                                                                .isSwitchedOn()));
        }
        return result;
    }

    @Override
    public String getTitle(Locale locale) {
        return "Hexabus Plugs";
    }
}
