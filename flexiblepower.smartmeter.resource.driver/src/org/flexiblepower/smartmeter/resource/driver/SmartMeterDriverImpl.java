package org.flexiblepower.smartmeter.resource.driver;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.flexiblepower.ral.ResourceDriver;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.smartmeter.device.SmartMeterDevice;
import org.flexiblepower.smartmeter.driver.interfaces.SmartMeterControlParameters;
import org.flexiblepower.smartmeter.driver.interfaces.SmartMeterDriver;
import org.flexiblepower.smartmeter.driver.interfaces.SmartMeterMeasurement;
import org.flexiblepower.smartmeter.driver.interfaces.SmartMeterState;
import org.flexiblepower.smartmeter.resource.driver.SmartMeterDriverImpl.Config;
import org.flexiblepower.time.TimeService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = ResourceDriver.class, immediate = true)
public class SmartMeterDriverImpl extends AbstractResourceDriver<SmartMeterState, SmartMeterControlParameters> implements
                                                                                                              SmartMeterDriver {
    SmartMeterMeasurement latestMeasurement;
    SmartMeterDevice meter;
    State latestState;
    Thread meterThread;

    final static class State implements SmartMeterState {

        private final SmartMeterMeasurement measurement;

        State(SmartMeterMeasurement measurement) {
            this.measurement = measurement;
        }

        @Override
        public SmartMeterMeasurement getMeasurement() {
            return measurement;
        }

        @Override
        public Date getTimeStamp() {
            return measurement.getTimestamp();
        }

        @Override
        public BigDecimal getCurrentPowerConsumptionW() {
            return measurement.getCurrentPowerConsumptionW();
        }

        @Override
        public BigDecimal getCurrentPowerProductionW() {
            return measurement.getCurrentPowerProductionW();
        }

        @Override
        public BigDecimal getElectricityConsumptionLowRateKwh() {
            return measurement.getElectricityConsumptionLowRateKwh();
        }

        @Override
        public BigDecimal getElectricityConsumptionNormalRateKwh() {
            return measurement.getElectricityConsumptionNormalRateKwh();
        }

        @Override
        public BigDecimal getElectricityProductionLowRateKwh() {
            return measurement.getElectricityProductionLowRateKwh();
        }

        @Override
        public BigDecimal getElectricityProductionNormalRateKwh() {
            return measurement.getElectricityProductionNormalRateKwh();
        }

        @Override
        public BigDecimal getGasConsumptionM3() {
            return measurement.getGasConsumptionM3();
        }

        @Override
        public boolean isConnected() {
            // TODO Auto-generated method stub
            return false;
        }
    }

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "smartmeter")
        String resourceId();

        @Meta.AD(deflt = "COM11")
        String getcomport();
    }

    private ScheduledFuture<?> scheduledFuture;

    private ServiceRegistration<?> observationProviderRegistration;

    private Config config;

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        try {
            logger.debug("Smart Meter Activated");

            Config config = Configurable.createConfigurable(Config.class, properties);

            String applianceId = config.resourceId();
            observationProviderRegistration = new ObservationProviderRegistrationHelper(this).observationType(SmartMeterMeasurement.class)
                                                                                             .observationOf(applianceId)
                                                                                             .observedBy(applianceId)
                                                                                             .register();

            meterThread = new Thread(new SmartMeterDevice(config.getcomport(), this), "Smart Meter Thread");
            meterThread.start();

            // scheduledFuture = schedulerService.scheduleAtFixedRate(this, 0, 5,
            // java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable e) {
            System.out.println("!!!!_---------------");
        }
    }

    @Deactivate
    public void deactivate() {
        scheduledFuture.cancel(false);
        observationProviderRegistration.unregister();
    }

    private ScheduledExecutorService schedulerService;

    @Reference
    public void setSchedulerService(ScheduledExecutorService schedulerService) {
        this.schedulerService = schedulerService;
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    private State getState() {
        return latestState;
    }

    public void setLatestState(State latestState) {
        this.latestState = latestState;
    }

    public SmartMeterMeasurement getLatestMeasurement() {
        return latestMeasurement;
    }

    public void setLatestMeasurement(SmartMeterMeasurement latestMeasurement) {

        publish(new Observation<SmartMeterState>(timeService.getTime(), new State(latestMeasurement)));
        logger.debug("Observation Published by smart meter");
        logger.debug(latestMeasurement.toString());
    }

    @Override
    public void setControlParameters(SmartMeterControlParameters resourceControlParameters) {
        // TODO Auto-generated method stub

    }
}
