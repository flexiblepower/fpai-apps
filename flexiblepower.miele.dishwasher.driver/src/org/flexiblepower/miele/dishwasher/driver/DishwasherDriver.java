package org.flexiblepower.miele.dishwasher.driver;

import static javax.measure.unit.NonSI.HOUR;
import static javax.measure.unit.NonSI.KWH;

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

import org.flexiblepower.protocol.mielegateway.api.ActionPerformer;
import org.flexiblepower.protocol.mielegateway.api.ActionResult;
import org.flexiblepower.protocol.mielegateway.api.MieleResourceDriver;
import org.flexiblepower.rai.values.Commodity;
import org.flexiblepower.rai.values.CommodityProfile;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherControlParameters;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;
import org.flexiblepower.time.TimeService;
import org.flexiblepower.time.TimeUtil;
import org.flexiblepower.ui.Widget;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;

public class DishwasherDriver extends MieleResourceDriver<DishwasherState, DishwasherControlParameters> implements
org.flexiblepower.ral.drivers.dishwasher.DishwasherDriver {
    private static final Logger log = LoggerFactory.getLogger(DishwasherDriver.State.class);

    static final class State implements DishwasherState {
        private final boolean isConnected;
        private final Date startTime;
        private final String program;

        State(Date startTime, String program) {
            isConnected = true;
            this.startTime = startTime;
            this.program = program;
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
        public Date getLatestStartTime() {
            return startTime;
        }

        @Override
        public CommodityProfile<Energy, Power> getEnergyProfile() {
            return CommodityProfile.create(Commodity.ELECTRICITY)
                                   .add(Measure.valueOf(1, HOUR), Measure.valueOf(1, KWH))
                                   .build();
        }
    }

    public DishwasherDriver(ActionPerformer actionPerformer, TimeService timeService) {
        super(actionPerformer, timeService);
    }

    private ScheduledExecutorService scheduler;

    @Reference
    public void setScheduledExecutorService(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    private DishwasherWidget widget;
    private ServiceRegistration<Widget> widgetRegistration;

    @Activate
    public void activate(BundleContext context) {
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

        currentState = new State(startTime, currentProgram);
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
                    ActionResult result = performAction("Start");
                    if (!result.isOk()) {
                        log.warn("Could not start the dishwasher: {}", result.getMessage());
                    }
                }
            }, diff.longValue(SI.SECOND), TimeUnit.SECONDS);
        }
    }
}
