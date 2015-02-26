package org.flexiblepower.smartmeter.resource.driver;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Date;
import java.util.Map;

import org.flexiblepower.protocol.rxtx.Connection;
import org.flexiblepower.protocol.rxtx.ConnectionFactory;
import org.flexiblepower.protocol.rxtx.SerialConnectionOptions;
import org.flexiblepower.protocol.rxtx.SerialConnectionOptions.Baudrate;
import org.flexiblepower.protocol.rxtx.SerialConnectionOptions.Databits;
import org.flexiblepower.protocol.rxtx.SerialConnectionOptions.Parity;
import org.flexiblepower.protocol.rxtx.SerialConnectionOptions.Stopbits;
import org.flexiblepower.ral.ResourceControlParameters;
import org.flexiblepower.ral.ResourceDriver;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.smartmeter.driver.interfaces.SmartMeterDriver;
import org.flexiblepower.smartmeter.driver.interfaces.SmartMeterState;
import org.flexiblepower.smartmeter.parser.DatagramParser;
import org.flexiblepower.smartmeter.parser.SmartMeterMeasurement;
import org.flexiblepower.smartmeter.resource.driver.SmartMeterDriverImpl.Config;
import org.osgi.framework.BundleContext;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = ResourceDriver.class, immediate = true)
public class SmartMeterDriverImpl extends AbstractResourceDriver<SmartMeterState, ResourceControlParameters> implements
                                                                                                            SmartMeterDriver {
    private SmartMeterMeasurement latestMeasurement;
    private volatile boolean running;

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "smartmeter")
        String resourceId();

        @Meta.AD(deflt = "COM11", description = "The name of the serial port to connect to")
        String port_name();
    }

    private ConnectionFactory connectionFactory;

    @Reference
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    private Config config;
    private Connection connection;

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) throws IOException {

        running = false;
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {

            logger.debug("Smart Meter Activated");
            config = Configurable.createConfigurable(Config.class, properties);
            String applianceId = config.resourceId();
            connection = connectionFactory.openSerialConnection(config.port_name(),
                                                                new SerialConnectionOptions(Baudrate.B9600,
                                                                                            Databits.D7,
                                                                                            Stopbits.S1,
                                                                                            Parity.Even));
            running = true;
        } catch (Exception e) {
            logger.warn("Exception in activation of Serial Driver", e);
        }

        new Thread("Smart Meter Thread: " + config.resourceId()) {
            @Override
            public void run() {
                StringBuilder sb = new StringBuilder(1024);
                Reader reader = new InputStreamReader(connection.getInputStream());
                try {
                    while (running) {
                        int nextChar = reader.read();
                        if (nextChar < 0) {
                            break;
                        } else if (nextChar == '!') {
                            String datagram = sb.toString();
                            SmartMeterMeasurement measurement = DatagramParser.SINGLETON.parse(datagram);
                            measurement.setTimestamp(new Date());
                            update(measurement);
                        } else {
                            sb.append((char) nextChar);
                        }
                    }
                } catch (IOException ex) {
                    logger.error("I/O error while reading from smart meter", ex);
                } finally {
                    connection.close();
                }
            }
        }.start();
    }

    @Deactivate
    public void deactivate() {
        running = false;
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public SmartMeterMeasurement getLatestMeasurement() {
        return latestMeasurement;
    }

    void update(SmartMeterMeasurement latestMeasurement) {
        this.latestMeasurement = latestMeasurement;
        publishState(latestMeasurement);

        logger.debug("Updating state to {}", latestMeasurement);
    }

    @Override
    public void handleControlParameters(ResourceControlParameters resourceControlParameters) {
        // No control possible
    }
}
