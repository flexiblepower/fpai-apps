package org.flexiblepower.miele.dishwasher.driver;

import static javax.measure.unit.NonSI.HOUR;
import static javax.measure.unit.NonSI.KWH;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measure;

import org.flexiblepower.miele.dishwasher.driver.MieleDishwasherDriver.Config;
import org.flexiblepower.miele.protocol.MieleAppliance;
import org.flexiblepower.miele.protocol.MieleApplianceAction;
import org.flexiblepower.miele.protocol.MieleProtocol;
import org.flexiblepower.miele.protocol.constants.MieleApplianceConstants;
import org.flexiblepower.miele.protocol.messages.MieleDishWasherInfoMessage;
import org.flexiblepower.miele.protocol.messages.MieleGatewayMessage;
import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.flexiblepower.rai.values.EnergyProfile;
import org.flexiblepower.ral.ResourceDriver;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherControlParameters;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherDriver;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;
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
public class MieleDishwasherDriver extends AbstractResourceDriver<DishwasherState, DishwasherControlParameters> implements
                                                                                                               DishwasherDriver,
                                                                                                               Runnable {
    static final class State implements DishwasherState {
        private final boolean isConnected;
        private final Date startTime;
        private final String program;

        State(MieleDishWasherInfoMessage message) {
            isConnected = true;
            startTime = message.getStartTime();
            program = message.getProgram();
        }

        State() {
            isConnected = false;
            startTime = null;
            program = "No program selected";
        }

        @Override
        public boolean isConnected() {
            return isConnected;
        }

        @Override
        public Date getStartTime() {
            return startTime;
        }

        @Override
        public String getProgram() {
            return program;
        }

        @Override
        public EnergyProfile getEnergyProfile() {
            return EnergyProfile.create().add(Measure.valueOf(1, HOUR), Measure.valueOf(1, KWH)).build();
        }
    }

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "dishwasher")
        String resourceId();

        @Meta.AD(deflt = "DW_G1000")
        String mieleApplianceType();

        @Meta.AD(deflt = "DW_G1000.-1609539276")
        String mieleApplianceId();
    }

    private Config config;

    private MieleAppliance appliance;

    private ScheduledFuture<?> scheduledFuture;

    private ServiceRegistration<?> observationProviderRegistration;

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        try {
            config = Configurable.createConfigurable(Config.class, properties);
            appliance = new MieleAppliance(config.mieleApplianceType(), config.mieleApplianceId());

            observationProviderRegistration = new ObservationProviderRegistrationHelper(this).observationType(DishwasherState.class)
                                                                                             .observationOf(config.resourceId())
                                                                                             .register();

            scheduledFuture = scheduler.scheduleAtFixedRate(this, 0, 5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (RuntimeException ex) {
            logger.error("Error while activating the MieleDishwasherDriver", ex);
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
        if (observationProviderRegistration != null) {
            observationProviderRegistration.unregister();
            observationProviderRegistration = null;
        }
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
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

    @Override
    public void run() {
        MieleDishWasherInfoMessage infoMsg = getInfoMessage();
        DishwasherState state = infoMsg == null ? new State() : new State(infoMsg);
        publish(new Observation<DishwasherState>(timeService.getTime(), state));
    }

    private MieleDishWasherInfoMessage getInfoMessage() {
        MieleApplianceAction action = MieleApplianceAction.createInfoAction(appliance);
        try {
            MieleGatewayMessage info = mieleProtocol.performApplianceAction(appliance, action);
            if (info instanceof MieleDishWasherInfoMessage) {
                return (MieleDishWasherInfoMessage) info;
            }
        } catch (IOException e) {
            logger.error("Could not reach device", e);
        }
        return null;
    }

    @Override
    public void setControlParameters(DishwasherControlParameters resourceControlParameters) {
        if (resourceControlParameters.getStartProgram()) {
            setDishwasherStart();
        }
    }

    public void setDishwasherStart() {
        int state = getInfoMessage().getApplianceState();

        if (state == MieleApplianceConstants.MA_STATE_WAITING) {
            logger.debug("Sending request to start the dishwasher program");
            MieleApplianceAction action = MieleApplianceAction.createAction(appliance,
                                                                            MieleApplianceConstants.APPLIANCE_ACTION_START);
            try {
                mieleProtocol.performApplianceAction(appliance, action);
            } catch (IOException e) {
                logger.error("Could not start dishwasher program", e);
            }
        }
    }
}
