package org.flexiblepower.smartmeter.device;

import java.util.Date;

import org.flexiblepower.smartmeter.driver.interfaces.SmartMeterMeasurement;
import org.flexiblepower.smartmeter.parser.DatagramParser;
import org.flexiblepower.smartmeter.resource.driver.SmartMeterDriverImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmartMeterListenerImpl implements SmartMeterListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private SmartMeterMeasurement currentMeasurement;

    private final DatagramParser datagramParser;

    public SmartMeterListenerImpl() {
        datagramParser = new DatagramParser();
    }

    @Override
    public SmartMeterMeasurement put(String datagram, SmartMeterDriverImpl resourceDriver) {

        if (logger.isTraceEnabled()) {
            logger.trace(datagram);
        }

        SmartMeterMeasurement measurement = datagramParser.parse(datagram);

        measurement.setTimestamp(new Date());
        currentMeasurement = measurement;

        resourceDriver.setLatestMeasurement(measurement);
        return measurement;
    }

    @Override
    public SmartMeterMeasurement getCurrentMeasurement() {
        return currentMeasurement;
    }

    @Override
    public SmartMeterMeasurement put(String datagram) {
        // TODO Auto-generated method stub
        return null;
    }
}
