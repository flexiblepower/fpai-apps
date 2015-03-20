package org.flexiblepower.simulation.profile.timeshifter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measure;
import javax.measure.unit.SI;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.efi.TimeShifterResourceManager;
import org.flexiblepower.efi.timeshifter.TimeShifterRegistration;
import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.MessageHandler;
import org.flexiblepower.ral.values.CommoditySet;
import org.flexiblepower.simulation.profile.timeshifter.TimeShifterProfileManager.Config;
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
public class TimeShifterProfileManager implements TimeShifterResourceManager, Runnable, MessageHandler {

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "timeshifterprofilemanager", description = "Resource identifier")
        String resourceId();

        @Meta.AD(deflt = "main.csv",
                 description = "Main CSV file with references to valid from times, end before times, and power data profile files")
                String
                filename();

        @Meta.AD(deflt = "5", description = "Delay between updates will be send out in seconds")
        int updateDelay();
    }

    private static final Logger logger = LoggerFactory.getLogger(TimeShifterProfileManager.class);

    private BundleContext bundleContext;
    private Config config;
    private Connection connection;
    private FlexiblePowerContext context;
    private ScheduledFuture<?> scheduledFuture;
    private List<TimeShifterProfile> timeShifterProfiles;

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) throws IOException {
        try {
            this.bundleContext = bundleContext;
            config = Configurable.createConfigurable(Config.class, properties);

            loadMainData(getInputStreamForFilename(config.filename()));

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

    private InputStream getInputStreamForFilename(String filename) throws IOException {
        File file = new File(filename); // For running from current directory
        if (file.exists() && file.isFile()) {
            return new FileInputStream(file);
        } else {
            file = new File("res/" + filename); // For running in Eclipse
            if (file.exists() && file.isFile()) {
                return new FileInputStream(file);
            } else {
                URL url = bundleContext.getBundle().getResource(filename);
                if (url != null) {
                    return url.openStream();
                } else {
                    throw new IllegalArgumentException("Could not get input stream for filename");
                }
            }
        }
    }

    @Override
    public void handleMessage(Object message) {
        // TODO Check if the message is of class TimeShifterAllocation
        // TODO Send AllocationStatusUpdate.ACCEPTED + STARTED
    }

    @Override
    public MessageHandler onConnect(Connection connection) {
        this.connection = connection;
        connection.sendMessage(new TimeShifterRegistration(config.resourceId(),
                                                           context.currentTime(),
                                                           Measure.zero(SI.SECOND),
                                                           CommoditySet.onlyElectricity));
        return this;
    }

    private double interpolate(float value1, float value2, double fraction) {
        return (1 - fraction) * value1 + fraction * value2;
    }

    private void loadMainData(InputStream is) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));

        timeShifterProfiles = new ArrayList<TimeShifterProfile>();
        String line = null;
        while ((line = bufferedReader.readLine()) != null) {
            if (!line.startsWith("#")) { // Line does not contain comment
                String[] split = line.split(",");
                if (split.length == 3) {
                    int validFrom = Integer.parseInt(split[0].trim());
                    int endBefore = Integer.parseInt(split[1].trim());
                    String powerProfileFile = split[2].trim();
                    NavigableMap<Float, Float> powerAtMinutesSinceStart = loadPowerData(getInputStreamForFilename(powerProfileFile));
                    timeShifterProfiles.add(new TimeShifterProfile(validFrom, endBefore, powerAtMinutesSinceStart));
                }
            }
        }
        bufferedReader.close();
    }

    private NavigableMap<Float, Float> loadPowerData(InputStream is) throws IOException {
        // Data gathered from: http://blog.oliverparson.co.uk/2011/01/appliance-survey-tumble-dryer-washing.html
        // Transformed to CSV using: http://arohatgi.info/WebPlotDigitizer/app

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));

        NavigableMap<Float, Float> powerAtMinutesSinceStart = new TreeMap<Float, Float>();
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

        return powerAtMinutesSinceStart;
    }

    @Override
    public synchronized void run() {
        try {
            if (connection != null) {
                Date currentTime = context.currentTime();
                // TODO Send TimeShifterUpdate if a simulated appliance wants to be started (current time >= valid from)
                // TODO Send AllocationStatusUpdate.FINISHED if simulated appliance has finished
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
