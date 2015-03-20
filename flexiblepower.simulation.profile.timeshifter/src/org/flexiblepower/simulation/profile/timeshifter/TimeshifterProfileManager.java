package org.flexiblepower.simulation.profile.timeshifter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measure;
import javax.measure.unit.SI;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.efi.UncontrolledResourceManager;
import org.flexiblepower.efi.uncontrolled.UncontrolledMeasurement;
import org.flexiblepower.efi.uncontrolled.UncontrolledRegistration;
import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.MessageHandler;
import org.flexiblepower.ral.values.CommodityMeasurables;
import org.flexiblepower.ral.values.CommoditySet;
import org.flexiblepower.ral.values.ConstraintListMap;
import org.flexiblepower.simulation.profile.timeshifter.TimeshifterProfileManager.Config;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = Endpoint.class, immediate = true)
public class TimeshifterProfileManager implements UncontrolledResourceManager, Runnable, MessageHandler {

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "timeshifterprofilemanager", description = "Resource identifier")
        String resourceId();

        @Meta.AD(deflt = "washingmachine1.csv", description = "CSV file with power data profile")
        String filename();

        @Meta.AD(deflt = "false", description = "Generates power [true] or consumes power [false]")
        boolean generatesPower();

        @Meta.AD(deflt = "5", description = "Delay between updates will be send out in seconds")
        int updateDelay();
    }

    private static final Logger logger = LoggerFactory.getLogger(TimeshifterProfileManager.class);

    private Config config;
    private Connection connection;
    private FlexiblePowerContext context;
    private ScheduledFuture<?> scheduledFuture;
    private NavigableMap<Float, Float> powerAtMinutesSinceStart = new TreeMap<Float, Float>();

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) throws IOException {
        try {
            config = Configurable.createConfigurable(Config.class, properties);

            try {
                File file = new File(config.filename()); // For running from current directory
                if (file.exists() && file.isFile()) {
                    loadData(new FileInputStream(file));
                } else {
                    file = new File("res/" + config.filename()); // For running in Eclipse
                    if (file.exists() && file.isFile()) {
                        loadData(new FileInputStream(file));
                    } else {
                        URL url = bundleContext.getBundle().getResource(config.filename());
                        if (url != null) {
                            loadData(url.openStream());
                        } else {
                            throw new IllegalArgumentException("Could not load power profile data");
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Could not load power profile data", e);
                throw (e);
            }

            scheduledFuture = context.scheduleAtFixedRate(this,
                                                          Measure.valueOf(0, SI.SECOND),
                                                          Measure.valueOf(config.updateDelay(), SI.SECOND));
        } catch (RuntimeException ex) {
            logger.error("Error during initialization of the timeshifter profile manager: " + ex.getMessage(), ex);
            deactivate();
            throw ex;
        }
    }

    @Deactivate
    public void deactivate() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
    }

    @Override
    public void disconnected() {
        connection = null;
    }

    @Override
    public void handleMessage(Object message) {
        // We do not expect messages
    }

    @Override
    public MessageHandler onConnect(Connection connection) {
        this.connection = connection;
        connection.sendMessage(new UncontrolledRegistration(config.resourceId(),
                                                            context.currentTime(),
                                                            Measure.zero(SI.SECOND),
                                                            CommoditySet.onlyElectricity,
                                                            ConstraintListMap.create().build()));
        return this;
    }

    private double interpolate(float value1, float value2, double fraction) {
        return (1 - fraction) * value1 + fraction * value2;
    }

    private void loadData(InputStream is) throws IOException {
        // Data gathered from: http://blog.oliverparson.co.uk/2011/01/appliance-survey-tumble-dryer-washing.html
        // Transformed to CSV using: http://arohatgi.info/WebPlotDigitizer/app

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));

        powerAtMinutesSinceStart = new TreeMap<Float, Float>();
        String line = null;
        while ((line = bufferedReader.readLine()) != null) {
            if (!line.startsWith("#")) { // Line does not contain comment
                String[] split = line.split(",");
                if (split.length == 2) {
                    float minute = Float.parseFloat(split[0].trim());
                    float powerValue = Float.parseFloat(split[1].trim());
                    powerAtMinutesSinceStart.put(minute, powerValue);
                }
            }
        }
        bufferedReader.close();
    }

    @Override
    public synchronized void run() {
        try {
            if (connection != null) {
                float minutesSinceStart = (float) 0.5; // TODO Calculate actual minutes since start of profile
                Float minuteBelow = powerAtMinutesSinceStart.lowerKey(minutesSinceStart);
                Float minuteAbove = powerAtMinutesSinceStart.higherKey(minutesSinceStart);
                float powerValueBelow = minuteBelow == null ? 0 : powerAtMinutesSinceStart.get(minuteBelow);
                float powerValueAbove = minuteAbove == null ? 0 : powerAtMinutesSinceStart.get(minuteAbove);
                double fraction = (minutesSinceStart - minuteBelow) / (minuteAbove - minuteBelow);
                double interpolatedPowerValue = interpolate(powerValueBelow, powerValueAbove, fraction);
                if (config.generatesPower()) {
                    interpolatedPowerValue = -interpolatedPowerValue;
                }
                connection.sendMessage(new UncontrolledMeasurement(config.resourceId(),
                                                                   context.currentTime(),
                                                                   context.currentTime(),
                                                                   CommodityMeasurables.create()
                                                                                       .electricity(Measure.valueOf(interpolatedPowerValue,
                                                                                                                    SI.WATT))
                                                                                       .build()));
            }
        } catch (Exception e) {
            logger.error("Error while running timeshifter profile manager", e);
        }
    }

    @Reference
    public void setContext(FlexiblePowerContext context) {
        this.context = context;
    }
}
