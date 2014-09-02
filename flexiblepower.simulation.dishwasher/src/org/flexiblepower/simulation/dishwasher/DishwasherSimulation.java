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
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.protocol.mielegateway.api.ActionPerformer;
import org.flexiblepower.protocol.mielegateway.api.MieleResourceDriver;
import org.flexiblepower.rai.values.Commodity;
import org.flexiblepower.rai.values.CommodityProfile;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherControlParameters;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;
import org.flexiblepower.simulation.dishwasher.DishwasherSimulation.Config;
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
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = Endpoint.class, immediate = true)
public class DishwasherSimulation extends MieleResourceDriver<DishwasherState, DishwasherControlParameters> implements
org.flexiblepower.ral.drivers.dishwasher.DishwasherDriver
{
    private static final Logger log = LoggerFactory.getLogger(DishwasherSimulation.State.class);

    interface Config {
        @Meta.AD(deflt = "true", description = "Device is connected to the driver")
        boolean isConnected();

        @Meta.AD(deflt = "0", description = "Latest starttime of dishwasher (as given on device)")
        Date latestStartTime();

        @Meta.AD(deflt = "No program selected", description = "Device is connected to the driver")
        String program();

    }

    static final class State implements DishwasherState {
        private final boolean isConnected;
        private final Date startTime;
        private final Date latestStartTime;
        private final String program;

        State(Date startTime, Date latestStartTime, String program) {
            isConnected = true;
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
        public CommodityProfile<Energy, Power> getEnergyProfile() {
            return CommodityProfile.create(Commodity.ELECTRICITY)
                    .add(Measure.valueOf(1, HOUR), Measure.valueOf(1000, SI.WATT))
                    .build();
        }

    }

    public DishwasherSimulation(ActionPerformer actionPerformer, TimeService timeService) {
        super(actionPerformer, timeService);
    }

    private ScheduledExecutorService scheduler;

    @Reference
    public void setScheduledExecutorService(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    private DishwasherWidget widget;
    private ServiceRegistration<Widget> widgetRegistration;
    private Config configuration;

    @Activate
    public void activate(BundleContext context, Map<String, Object> properties) {
        configuration = Configurable.createConfigurable(Config.class, properties);

        Measurable<Duration> diff = TimeUtil.difference(timeService.getTime(),
                                                        configuration.latestStartTime());

        if (runFuture != null && !runFuture.isDone()) {
            runFuture.cancel(false);
        }

        runFuture = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                log.debug("Started by device");
                Date currentTime = timeService.getTime();
                currentState = new State(currentTime, currentState.getLatestStartTime(), currentState.getProgram());
                publishState(currentState);
            }
        }, diff.longValue(SI.SECOND), TimeUnit.SECONDS);

        widget = new DishwasherWidget(this, timeService);
        widgetRegistration = context.registerService(Widget.class, widget, null);
    }

    @Deactivate
    public void deactivate() {
        widgetRegistration.unregister();
    }

    private volatile State currentState;

    @Override
    public void updateState(Map<String, String> information) {
        // TODO: There is much more information, what to do with it?
        // String state = information.get("State");
        String startTimeString = information.get("Start Time");
        Date startTime = parseDate(startTimeString);

        // Integer smartStart = parseTime(information.get("Smart Start"));

        String currentProgram = information.get("Program");
        // String phase = information.get("Phase");
        // Integer remainingTime = parseTime(information.get("Remaining Time"));
        // Integer duration = parseTime(information.get("Duration"));

        // TODO: or should I parse the latest start time?? -- Jan
        currentState = new State(startTime, currentState.getLatestStartTime(), currentProgram);
        publishState(currentState);
    }

    State getCurrentState() {
        return currentState;
    }

    private volatile Future<?> runFuture;

    @Override
    public void handleControlParameters(DishwasherControlParameters resourceControlParameters) {
        if (resourceControlParameters.getProgram().equals(currentState.getProgram())) {
            Measurable<Duration> diff = TimeUtil.difference(timeService.getTime(),
                                                            resourceControlParameters.getStartTime());

            if (runFuture != null && !runFuture.isDone()) {
                runFuture.cancel(false);
            }

            runFuture = scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    log.debug("Started by manager");
                    Date currentTime = timeService.getTime();
                    currentState = new State(currentTime, currentState.getLatestStartTime(), currentState.getProgram());
                    publishState(currentState);
                }
            },
            diff.longValue(SI.SECOND),
            TimeUnit.SECONDS);
        }
    }

    // @Override
    // public synchronized void run() {
    // Date currentTime = timeService.getTime();
    // double timeSinceUpdate = (currentTime.getTime() - currentState.getStartTime().getTime()) / 1000.0; // in seconds
    // logger.debug("Dishwasher simulation step. Program={} Timestep={}s", currentState.getProgram(), timeSinceUpdate);
    // if (currentState.getStartTime() != null) {
    // // Machine has not started yet
    // if (currentTime.after(configuration.latestStartTime())) {
    // // you have to start now!
    // log.debug("Starting dishwasher, as it is after latest start time");
    // currentState = new State(currentTime, currentState.getProgram());
    // publishState(currentState);
    // } else {
    // log.debug("Not starting yet, it is before latest start time");
    // }
    // }
    // }
}
