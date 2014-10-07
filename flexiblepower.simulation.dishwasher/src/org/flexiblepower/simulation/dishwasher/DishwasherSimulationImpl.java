package org.flexiblepower.simulation.dishwasher;

import static javax.measure.unit.NonSI.HOUR;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.unit.SI;

import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.MessageHandler;
import org.flexiblepower.rai.values.CommodityProfile;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherControlParameters;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherDriver;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.simulation.api.dishwasher.DishwasherSimulation;
import org.flexiblepower.simulation.dishwasher.DishwasherSimulationImpl.Config;
import org.flexiblepower.time.TimeService;
import org.flexiblepower.time.TimeUtil;
import org.flexiblepower.ui.Widget;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = { Endpoint.class, DishwasherSimulation.class }, immediate = true)
public class DishwasherSimulationImpl
    extends AbstractResourceDriver<DishwasherState, DishwasherControlParameters>
    implements DishwasherDriver, DishwasherSimulation {

    private static final Logger log = LoggerFactory.getLogger(DishwasherSimulationImpl.State.class);

    @Meta.OCD
    interface Config {
        String resource_id();
    }

    public static final class State implements DishwasherState {
        private final boolean isConnected;
        private final Date startTime;
        private final Date latestStartTime;
        private final String program;

        State(boolean isConnected, Date startTime, Date latestStartTime, String program) {
            this.isConnected = isConnected;
            this.startTime = startTime;
            this.latestStartTime = latestStartTime;
            this.program = program;
        }

        State() {
            isConnected = false;
            startTime = null;
            latestStartTime = null;
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
        public Date getLatestStartTime() {
            return latestStartTime;
        }

        @Override
        public String getProgram() {
            return program;
        }

        @Override
        public CommodityProfile getEnergyProfile() {
            return CommodityProfile.create()
                                   .duration(Measure.valueOf(1, HOUR))
                                   .electricity(Measure.valueOf(1000, SI.WATT)).next().build();
        }

    }

    private ScheduledExecutorService scheduler;

    @Reference
    public void setScheduledExecutorService(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    private DishwasherWidget widget;
    private ServiceRegistration<Widget> widgetRegistration;

    @Activate
    public void activate(BundleContext context, Map<String, Object> properties) {
        setIdle();
        widget = new DishwasherWidget(this, timeService);
        widgetRegistration = context.registerService(Widget.class, widget, null);
        log.info("Activated");
    }

    @Deactivate
    public void deactivate() {
        cancelJob();
        update(new State(false, null, null, null));

        widgetRegistration.unregister();
        log.info("Deactivated");
    }

    private volatile State currentState;

    @Override
    public DishwasherState getLatestState() {
        return currentState;
    }

    private volatile Future<?> future;

    private void cancelJob() {
        if (future != null) {
            if (!future.isDone()) {
                future.cancel(false);
            }
            future = null;
        }
    }

    @Override
    public void handleControlParameters(final DishwasherControlParameters resourceControlParameters) {
        log.debug("Handle controlParameters: {}", resourceControlParameters);
        if (resourceControlParameters.getProgram().equals(currentState.getProgram())) {
            Measurable<Duration> diff = TimeUtil.difference(timeService.getTime(),
                                                            resourceControlParameters.getStartTime());

            cancelJob();

            final Date latestStartTime = currentState.getLatestStartTime();
            final String program = currentState.getProgram();
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    log.debug("Started by manager");
                    update(new State(true, timeService.getTime(), latestStartTime, program));
                }
            };
            log.debug("Scheduling start over {}", diff);
            future = scheduler.schedule(runnable,
                                        diff.longValue(SI.SECOND),
                                        TimeUnit.SECONDS);

        } else {
            log.warn("Trying to start a different program (current={}, controlled={})",
                     currentState.getProgram(),
                     resourceControlParameters.getProgram());
        }
    }

    @Override
    public void setIdle() {
        cancelJob();
        update(new State(true, null, null, null));
    }

    @Override
    public void setProgram(final String program, final Date latestStartTime) {
        cancelJob();

        final Measurable<Duration> diff = TimeUtil.difference(timeService.getTime(), latestStartTime);
        future = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                update(new State(true, timeService.getTime(), latestStartTime, program));
                future = null;
            }
        }, diff.longValue(SI.SECOND), TimeUnit.SECONDS);
        update(new State(true, null, latestStartTime, program));
    }

    private void update(State state) {
        currentState = state;
        publishState(state);
    }

    @Override
    public MessageHandler onConnect(Connection connection) {
        MessageHandler messageHandler = super.onConnect(connection);
        publishState(currentState);
        return messageHandler;
    }
}
