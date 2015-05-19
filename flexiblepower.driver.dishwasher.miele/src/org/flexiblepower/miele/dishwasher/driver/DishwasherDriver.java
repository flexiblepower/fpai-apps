package org.flexiblepower.miele.dishwasher.driver;

import static javax.measure.unit.NonSI.HOUR;
import static javax.measure.unit.SI.KILO;
import static javax.measure.unit.SI.WATT;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.Future;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.protocol.mielegateway.api.ActionPerformer;
import org.flexiblepower.protocol.mielegateway.api.ActionResult;
import org.flexiblepower.protocol.mielegateway.api.MieleResourceDriver;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherControlParameters;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;
import org.flexiblepower.ral.values.CommodityProfile;
import org.flexiblepower.time.TimeUtil;
import org.flexiblepower.ui.Widget;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DishwasherDriver extends MieleResourceDriver<DishwasherState, DishwasherControlParameters> implements
                                                                                                       org.flexiblepower.ral.drivers.dishwasher.DishwasherDriver {
    private static final Logger logger = LoggerFactory.getLogger(DishwasherDriver.State.class);

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
        public CommodityProfile getEnergyProfile() {
            return CommodityProfile.create()
                                   .duration(Measure.valueOf(1, HOUR)).electricity(Measure.valueOf(1, KILO(WATT)))
                                   .build();
        }
    }

    private final DishwasherWidget widget;
    private final ServiceRegistration<Widget> widgetRegistration;

    public DishwasherDriver(ActionPerformer actionPerformer, FlexiblePowerContext context, BundleContext bundleContext) {
        super(actionPerformer, context);
        widget = new DishwasherWidget(this);
        widgetRegistration = bundleContext.registerService(Widget.class, widget, null);
    }

    @Override
    public void close() {
        super.close();
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
        publishMieleState(currentState);
    }

    State getCurrentState() {
        return currentState;
    }

    public void startNow() {
        ActionResult result = performAction("Start");
        if (!result.isOk()) {
            logger.warn("Could not start the dishwasher: {}", result.getMessage());
        }
    }

    private volatile Future<?> runFuture;

    @Override
    public void handleControlParameters(DishwasherControlParameters resourceControlParameters) {
        if (resourceControlParameters.getProgram().equals(currentState.getProgram())) {
            Measurable<Duration> diff = TimeUtil.difference(context.currentTime(),
                                                            resourceControlParameters.getStartTime());

            if (runFuture != null && !runFuture.isDone()) {
                runFuture.cancel(false);
            }

            runFuture = context.schedule(new Runnable() {
                @Override
                public void run() {
                    startNow();
                }
            }, diff);
        }
    }
}
