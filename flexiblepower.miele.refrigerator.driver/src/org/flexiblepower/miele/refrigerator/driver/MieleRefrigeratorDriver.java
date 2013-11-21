package org.flexiblepower.miele.refrigerator.driver;

import static javax.measure.unit.SI.CELSIUS;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Temperature;

import org.flexiblepower.miele.protocol.MieleAppliance;
import org.flexiblepower.miele.protocol.MieleApplianceAction;
import org.flexiblepower.miele.protocol.MieleProtocol;
import org.flexiblepower.miele.protocol.constants.MieleApplianceConstants;
import org.flexiblepower.miele.protocol.messages.MieleFridgeFreezerInfoMessage;
import org.flexiblepower.miele.protocol.messages.MieleGatewayMessage;
import org.flexiblepower.miele.refrigerator.driver.MieleRefrigeratorDriver.Config;
import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.flexiblepower.ral.ResourceDriver;
import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorControlParameters;
import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorDriver;
import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorState;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.time.TimeService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = ResourceDriver.class)
public class MieleRefrigeratorDriver extends AbstractResourceDriver<RefrigeratorState, RefrigeratorControlParameters> implements
                                                                                                                     RefrigeratorDriver,
                                                                                                                     Runnable {
    static final class State implements RefrigeratorState {
        private final boolean isConnected;
        private final Measurable<Temperature> currTemp, targetTemp, minTemp;
        private final boolean supercool;

        State() {
            isConnected = false;
            currTemp = targetTemp = minTemp = null;
            supercool = false;
        }

        State(MieleFridgeFreezerInfoMessage msg) {
            isConnected = true;
            currTemp = Measure.valueOf(msg.getRefrigeratorTemperature(), CELSIUS);
            targetTemp = Measure.valueOf(msg.getRefrigeratorTargetTemperature(), CELSIUS);
            minTemp = Measure.valueOf(4, CELSIUS);
            supercool = msg.getRefrigeratorState() == MieleApplianceConstants.MA_STATE_SUPERCOOL;
        }

        @Override
        public boolean isConnected() {
            return isConnected;
        }

        @Override
        public Measurable<Temperature> getCurrentTemperature() {
            return currTemp;
        }

        @Override
        public Measurable<Temperature> getTargetTemperature() {
            return targetTemp;
        }

        @Override
        public Measurable<Temperature> getMinimumTemperature() {
            return minTemp;
        }

        @Override
        public boolean getSupercoolMode() {
            return supercool;
        }
    }

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "refrigerator")
        String resourceId();

        @Meta.AD(deflt = "MieleRE")
        String mieleApplianceType();

        @Meta.AD(deflt = "KFN_8767.-2039584")
        String mieleApplianceId();
    }

    private Config config;

    private MieleAppliance appliance;

    private ScheduledFuture<?> scheduledFuture;
    private ServiceRegistration<?> observationProviderRegistration;

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        config = Configurable.createConfigurable(Config.class, properties);
        appliance = new MieleAppliance(config.mieleApplianceType(), config.mieleApplianceId());

        observationProviderRegistration = new ObservationProviderRegistrationHelper(this).observationType(RefrigeratorState.class)
                                                                                         .observationOf(config.resourceId())
                                                                                         .register();

        scheduledFuture = scheduler.scheduleAtFixedRate(this, 0, 5, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Deactivate
    public void deactivate() {
        scheduledFuture.cancel(false);
        observationProviderRegistration.unregister();
    }

    private ScheduledExecutorService scheduler;

    @Reference
    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    private MieleProtocol mieleProtocol;

    @Reference
    public void setMieleProtocol(MieleProtocol mieleProtocol) {
        this.mieleProtocol = mieleProtocol;
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    @Override
    public void run() {
        final Date now = timeService.getTime();
        publish(new Observation<RefrigeratorState>(now, getState()));
    }

    private RefrigeratorState getState() {
        try {
            MieleGatewayMessage infoMsg = mieleProtocol.performApplianceAction(appliance,
                                                                               MieleApplianceAction.createInfoAction(appliance));
            if (infoMsg instanceof MieleFridgeFreezerInfoMessage) {
                return new State((MieleFridgeFreezerInfoMessage) infoMsg);
            } else {
                logger.error("Illegal configuration, got different device message than expected");
                return new State();
            }
        } catch (IOException e) {
            logger.warn("Could not connect to the refrigerator or the gateway", e);
            return new State();
        }
    }

    @Override
    public void setControlParameters(RefrigeratorControlParameters resourceControlParameters) {
        try {
            if (resourceControlParameters.getSupercoolMode()) {
                mieleProtocol.performApplianceAction(appliance,
                                                     MieleApplianceAction.createAction(appliance,
                                                                                       MieleApplianceConstants.APPLIANCE_ACTION_PARAM_SUPERCOOLING_ON));
            } else {
                mieleProtocol.performApplianceAction(appliance,
                                                     MieleApplianceAction.createAction(appliance,
                                                                                       MieleApplianceConstants.APPLIANCE_ACTION_PARAM_SUPERCOOLING_OFF));
            }
        } catch (IOException e) {
            logger.warn("Could not change the supercool setting", e);
        }
    }
}
