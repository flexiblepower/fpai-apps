package org.flexiblepower.driver.pv.sma.impl;

import static javax.measure.unit.NonSI.KWH;
import static javax.measure.unit.SI.WATT;

import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import javax.bluetooth.BluetoothStateException;
import javax.measure.DecimalMeasure;
import javax.measure.Measure;
import javax.measure.unit.SI;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.driver.pv.sma.SmaInverterDriver;
import org.flexiblepower.driver.pv.sma.SmaInverterState;
import org.flexiblepower.driver.pv.sma.impl.SmaResourceDriver.Config;
import org.flexiblepower.driver.pv.sma.impl.data.ProductionInfo;
import org.flexiblepower.driver.pv.sma.impl.data.SpotAcInfo;
import org.flexiblepower.driver.pv.sma.widget.SmaWidget;
import org.flexiblepower.ral.ResourceControlParameters;
import org.flexiblepower.ral.ResourceDriver;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.ui.Widget;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;
import com.luckycatlabs.sunrisesunset.dto.Location;

@Component(designateFactory = Config.class, immediate = true, provide = { ResourceDriver.class })
public class SmaResourceDriver extends AbstractResourceDriver<SmaInverterState, ResourceControlParameters> implements
    SmaInverterDriver,
    Runnable {
    public interface Config {
        @Meta.AD(description = "The unique resource identifier", deflt = "sma-inverter")
        String resourceId();

        @Meta.AD(description = "The MAC Address of the bluetooth receiver on the SMA inverter", deflt = "00802529EC47")
        String macAddress();

        @Meta.AD(description = "The password of the bluetooth receiver (default 0000)", deflt = "0000")
        String password();

        @Meta.AD(description = "The latitude of the inverter location, used for sunset/rise", deflt = "0")
        String latitude();

        @Meta.AD(description = "The longitude of the inverter location, used for sunset/rise", deflt = "0")
        String longitude();

        @Meta.AD(description = "Offset in seconds to start before sunrise and end after sunset", deflt = "900")
        int sunUpOffset();

        @Meta.AD(description = "The timezone identifier of the machine running this code, used for sunset/rise",
                deflt = "Europe/Amsterdam")
        String timezone();

        @Meta.AD(description = "The update frequency at which the inverter will be read in seconds", deflt = "60")
        int updateFrequency();
    }

    private static final Logger logger = LoggerFactory.getLogger(SmaResourceDriver.class);

    private FlexiblePowerContext context;

    @Reference
    public void setContext(FlexiblePowerContext context) {
        this.context = context;
    }

    private PersistentSMA persistentSMA;

    private SunriseSunsetCalculator sunriseSunsetCalculator;

    private int sunUpOffset;

    private ScheduledFuture<?> schedule;

    private SmaInverterState currentState;

    private ServiceRegistration<Widget> serviceRegistration;

    private PVObservationProvider pvObservationProvider;

    @Activate
    public void activate(BundleContext context, Map<String, ?> properties) throws BluetoothStateException {
        Config config = Configurable.createConfigurable(Config.class, properties);

        try {
            persistentSMA = new PersistentSMA(config.macAddress(), config.password());
            sunriseSunsetCalculator = new SunriseSunsetCalculator(new Location(config.latitude(), config.longitude()),
                                                                  config.timezone());
            sunUpOffset = config.sunUpOffset();

            pvObservationProvider = new PVObservationProvider(context, config.resourceId(), this.context);
            schedule = this.context.scheduleAtFixedRate(this,
                                                        Measure.valueOf(0, SI.SECOND),
                                                        Measure.valueOf(config.updateFrequency(), SI.SECOND));

            SmaWidget smaWidget = new SmaWidget(this);
            serviceRegistration = context.registerService(Widget.class, smaWidget, null);
        } catch (Exception ex) {
            logger.error("Exception while activating driver: " + ex.getMessage(), ex);
            deactivate();
        }
    }

    @Deactivate
    public void deactivate() {
        if (pvObservationProvider != null) {
            pvObservationProvider.close();
            pvObservationProvider = null;
        }
        if (schedule != null) {
            schedule.cancel(false);
            schedule = null;
        }
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
            serviceRegistration = null;
        }
        persistentSMA.close();
    }

    @Override
    public SmaInverterState getCurrentState() {
        return currentState;
    }

    private boolean isSunUp() {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(context.currentTimeMillis());

        Calendar sunrise = sunriseSunsetCalculator.getOfficialSunriseCalendarForDate(now);
        sunrise.add(Calendar.SECOND, -sunUpOffset);

        Calendar sunset = sunriseSunsetCalculator.getOfficialSunsetCalendarForDate(now);
        sunset.add(Calendar.SECOND, sunUpOffset);

        return now.after(sunrise) && now.before(sunset);
    }

    @Override
    public void run() {
        State state = null;
        if (isSunUp()) {
            ProductionInfo productionInfo = persistentSMA.requestProductionInfo(3);
            SpotAcInfo spotAcInfo = persistentSMA.requestSpotAcInfo(3);
            if (productionInfo != null && spotAcInfo != null) {
                state = new State(DecimalMeasure.valueOf(spotAcInfo.getPower().negate(), WATT),
                                  context.currentTime(),
                                  // removed inverter time: productionInfo.getTimestamp()
                                  true,
                                  true,
                                  DecimalMeasure.valueOf(productionInfo.getLifetime(), KWH),
                                  DecimalMeasure.valueOf(productionInfo.getToday(), KWH));
                // Sun is up and there is a connection with the inverter
            } else {
                // Sun is up but there is no connection with the inverter
                state = State.createEmpty(true);
            }
        } else {
            // Sun is down and there is no connection with the inverter
            state = State.createEmpty(false);
        }
        publishState(state);
        pvObservationProvider.publish(state);
        currentState = state;
    }

    @Override
    protected void handleControlParameters(ResourceControlParameters controlParameters) {
        // Nothing to do
    }
}
