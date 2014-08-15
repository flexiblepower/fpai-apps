package org.flexiblepower.smartmeter.device;

import org.flexiblepower.smartmeter.driver.interfaces.SmartMeterMeasurement;
import org.flexiblepower.smartmeter.resource.driver.SmartMeterDriverImpl;

public interface SmartMeterListener {
    SmartMeterMeasurement put(String datagram);

    SmartMeterMeasurement getCurrentMeasurement();

    SmartMeterMeasurement put(String datagram, SmartMeterDriverImpl resourceDriver);
}
