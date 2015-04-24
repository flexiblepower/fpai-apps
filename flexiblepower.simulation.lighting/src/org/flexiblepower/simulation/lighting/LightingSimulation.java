package org.flexiblepower.simulation.lighting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.measure.Measure;
import javax.measure.unit.SI;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ext.SimpleObservationProvider;
import org.flexiblepower.ral.ResourceControlParameters;
import org.flexiblepower.ral.drivers.uncontrolled.PowerState;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrollableDriver;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.simulation.lighting.LightingSimulation.Config;
import org.flexiblepower.ui.Widget;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta.AD;
import aQute.bnd.annotation.metatype.Meta.OCD;

@Component(designateFactory = Config.class, provide = Endpoint.class, immediate = true)
public class LightingSimulation extends AbstractResourceDriver<PowerState, ResourceControlParameters> implements
UncontrollableDriver,
                                                                                                     Runnable {
    public static long filterDate(long date) {
        Calendar cal = new GregorianCalendar();
        cal.setTimeInMillis(date);
        cal.set(Calendar.YEAR, 2012);
        return cal.getTimeInMillis();
    }

    @OCD
    public interface Config {
        String applianceId();

        @AD(deflt = "50", description = "The number of lights that should be simulated")
        int nrOfLights();

        @AD(deflt = "35", description = "The amount of power that each light will use (in Watt)")
        double powerPerLight();
    }

    private final static Pattern TIMES_PATTERN = Pattern.compile("([0-9]{2})\\.([0-9]{2}) ([0-9]{2})\\.([0-9]{2})");

    private long[] switchTimes;

    private void loadData() throws IOException {
        long[] switchTimes = new long[366 * 2];
        int ix = 0;

        BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getClassLoader()
                                                                                   .getResourceAsStream("data/2012.txt")));
        String line = null;

        Calendar cal = new GregorianCalendar(2012, 0, 1);
        while ((line = reader.readLine()) != null) {
            Matcher matcher = TIMES_PATTERN.matcher(line);
            if (matcher.matches()) {
                int startHour = Integer.parseInt(matcher.group(1));
                int startMinutes = Integer.parseInt(matcher.group(2));
                int endHour = Integer.parseInt(matcher.group(3));
                int endMinutes = Integer.parseInt(matcher.group(4));

                cal.set(Calendar.HOUR_OF_DAY, startHour);
                cal.set(Calendar.MINUTE, startMinutes);
                switchTimes[ix++] = cal.getTimeInMillis();

                cal.set(Calendar.HOUR_OF_DAY, endHour);
                cal.set(Calendar.MINUTE, endMinutes);
                switchTimes[ix++] = cal.getTimeInMillis();

                cal.add(Calendar.HOUR, 24); // Go to the next day
            }
        }

        this.switchTimes = Arrays.copyOf(switchTimes, ix);
    }

    private final LightingWidget widget;

    public LightingSimulation() {
        widget = new LightingWidget(this);
    }

    private ScheduledFuture<?> schedule;

    private int nrOfLights;

    private double powerPerLight;

    private ServiceRegistration<?> registration;
    private ServiceRegistration<Widget> widgetRegistration;
    private SimpleObservationProvider<LightingUpdate> observationProvider;

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) throws IOException {
        Config config = Configurable.createConfigurable(Config.class, properties);

        nrOfLights = config.nrOfLights();
        powerPerLight = config.powerPerLight();

        loadData();

        context.scheduleAtFixedRate(this, Measure.valueOf(0, SI.SECOND), Measure.valueOf(10, SI.SECOND));

        widgetRegistration = bundleContext.registerService(Widget.class, widget, null);

        observationProvider = SimpleObservationProvider.create(this, LightingUpdate.class)
                .observationOf("'Straat Verlichting")
                .build();
    }

    @Deactivate
    public void deactivate() {
        widgetRegistration.unregister();
        schedule.cancel(false);
    }

    private FlexiblePowerContext context;

    @Reference
    public void setContext(FlexiblePowerContext context) {
        this.context = context;
    }

    private LightingUpdate update;

    private synchronized void update() {
        long now = filterDate(context.currentTimeMillis());
        int ix = Arrays.binarySearch(switchTimes, now);
        if (ix < 0) {
            ix = -ix - 1;
        }

        final Date time = new Date(now);
        final double power = nrOfLights * powerPerLight;

        final boolean isDay = (ix & 1) == 1;
        Date prevTime = ix <= 0 ? null : new Date(switchTimes[ix - 1]);
        Date nextTime = ix >= switchTimes.length ? null : new Date(switchTimes[ix]);

        update = new LightingUpdate(time,
                                    isDay ? prevTime : nextTime,
                                    isDay ? nextTime : prevTime,
                                    Measure.valueOf(isDay ? 0 : power, SI.WATT),
                                    nrOfLights);
        notifyAll();
    }

    @Override
    public void run() {
        update();
        publishState(update);
        observationProvider.publish(new Observation<LightingUpdate>(context.currentTime(), update));
    }

    public synchronized LightingUpdate getNextUpdate() {
        if (update == null) {
            update();
            return update;
        }
        return update;
    }

    @Override
    protected void handleControlParameters(ResourceControlParameters controlParameters) {
        // TODO Auto-generated method stub
    }
}
