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
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measure;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.efi.TimeShifterResourceManager;
import org.flexiblepower.efi.timeshifter.SequentialProfile;
import org.flexiblepower.efi.timeshifter.TimeShifterAllocation;
import org.flexiblepower.efi.timeshifter.TimeShifterRegistration;
import org.flexiblepower.efi.timeshifter.TimeShifterUpdate;
import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.MessageHandler;
import org.flexiblepower.ral.messages.AllocationStatus;
import org.flexiblepower.ral.messages.AllocationStatusUpdate;
import org.flexiblepower.ral.values.CommodityForecast;
import org.flexiblepower.ral.values.CommodityForecast.Builder;
import org.flexiblepower.ral.values.CommoditySet;
import org.flexiblepower.ral.values.UncertainMeasure;
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

        @Meta.AD(deflt = "washingmachine1.csv", description = "CSV file with power data profile")
        String filename();

        @Meta.AD(deflt = "0",
                 description = "This profile has validFrom value of bundle activation time plus this value in minutes")
        int validFrom();

        @Meta.AD(deflt = "600",
                 description = "This profile has endBefore value of bundle activation time plus this value in minutes")
        int endBefore();

        @Meta.AD(deflt = "5", description = "Delay between updates will be send out in seconds")
        int updateDelay();
    }

    private static final Logger logger = LoggerFactory.getLogger(TimeShifterProfileManager.class);

    private BundleContext bundleContext;
    private Config config;
    private Connection connection;
    private FlexiblePowerContext context;
    private ScheduledFuture<?> scheduledFuture;
    private float[] powerAtMinutesSinceStart;
    private boolean requestSent = false;
    private UUID allocationId = null;
    private boolean finishSent = false;
    private long bundleActivationTime;
    private long allocationReceivedTime;

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) throws IOException {
        try {
            this.bundleContext = bundleContext;
            config = Configurable.createConfigurable(Config.class, properties);

            bundleActivationTime = context.currentTimeMillis();
            loadData(getInputStreamForFilename(config.filename()));

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
        if (message != null && message instanceof TimeShifterAllocation) {
            TimeShifterAllocation allocation = (TimeShifterAllocation) message;
            allocationId = allocation.getResourceMessageId();

            AllocationStatusUpdate allocationStatusUpdate = new AllocationStatusUpdate(config.resourceId(),
                                                                                       context.currentTime(),
                                                                                       allocationId,
                                                                                       AllocationStatus.ACCEPTED,
                                                                                       "");
            connection.sendMessage(allocationStatusUpdate);

            allocationStatusUpdate = new AllocationStatusUpdate(config.resourceId(),
                                                                context.currentTime(),
                                                                allocationId,
                                                                AllocationStatus.STARTED,
                                                                "");
            connection.sendMessage(allocationStatusUpdate);
        }
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

    private void loadData(InputStream is) throws IOException {
        // Data gathered from: http://blog.oliverparson.co.uk/2011/01/appliance-survey-tumble-dryer-washing.html
        // Transformed to CSV using: http://arohatgi.info/WebPlotDigitizer/app

        // Read profile data from file. <minute since start, power in watt>
        // 0.351, 6.137
        // 0.708, 8.747
        // 1.065, 8.747
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));

        NavigableMap<Float, Float> powerAtMinutesSinceStartFromPlot = new TreeMap<Float, Float>();
        String line = null;
        while ((line = bufferedReader.readLine()) != null) {
            if (!line.startsWith("#")) { // Line does not contain comment
                String[] split = line.split(",");
                if (split.length == 2) {
                    float minute = Float.parseFloat(split[0].trim());
                    float powerValue = Float.parseFloat(split[1].trim());
                    powerAtMinutesSinceStartFromPlot.put(minute, powerValue);
                }
            }
        }
        bufferedReader.close();

        // Interpolate profile data to an array with index of whole minutes
        int lastMinute = (int) (float) powerAtMinutesSinceStartFromPlot.lastKey();
        powerAtMinutesSinceStart = new float[lastMinute];
        for (int i = 0; i < lastMinute; i++) {
            Entry<Float, Float> floorEntry = powerAtMinutesSinceStartFromPlot.floorEntry((float) i);
            Entry<Float, Float> ceilingEntry = powerAtMinutesSinceStartFromPlot.ceilingEntry((float) i);
            if (floorEntry != null && ceilingEntry != null) {
                float powerValue1 = floorEntry.getValue();
                float powerValue2 = ceilingEntry.getValue();
                double fraction = (i - floorEntry.getKey()) / (ceilingEntry.getKey() - floorEntry.getKey());
                powerAtMinutesSinceStart[i] = (float) interpolate(powerValue1, powerValue2, fraction);
            }
        }
    }

    @Override
    public synchronized void run() {
        try {
            if (connection != null) {
                Date currentTime = context.currentTime();

                if (!requestSent && bundleActivationTime + config.validFrom() <= currentTime.getTime()) {
                    Builder builder = CommodityForecast.create();
                    builder.duration(Measure.valueOf(60, SI.SECOND));
                    for (float element : powerAtMinutesSinceStart) {
                        builder.electricity(new UncertainMeasure<Power>(element, SI.WATT))
                               .next();
                    }

                    CommodityForecast commodityForecast = builder.build();
                    SequentialProfile sequentialProfile = new SequentialProfile(0,
                                                                                Measure.zero(SI.SECOND),
                                                                                commodityForecast);
                    List<SequentialProfile> sequentialProfiles = new ArrayList<SequentialProfile>();
                    sequentialProfiles.add(sequentialProfile);

                    TimeShifterUpdate timeShifterUpdate = new TimeShifterUpdate(config.resourceId(),
                                                                                currentTime,
                                                                                new Date(bundleActivationTime + 60000
                                                                                         * config.validFrom()),
                                                                                new Date(bundleActivationTime + 60000
                                                                                         * config.endBefore()),
                                                                                sequentialProfiles);
                    connection.sendMessage(timeShifterUpdate);
                    requestSent = true;
                }

                if (allocationId != null && !finishSent
                    && allocationReceivedTime + powerAtMinutesSinceStart.length * 60000 <= currentTime.getTime()) {
                    AllocationStatusUpdate allocationStatusUpdate = new AllocationStatusUpdate(config.resourceId(),
                                                                                               currentTime,
                                                                                               allocationId,
                                                                                               AllocationStatus.FINISHED,
                                                                                               "");
                    connection.sendMessage(allocationStatusUpdate);
                    finishSent = true;
                }
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
