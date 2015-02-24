package org.flexiblepower.simulation.dishwasher;

import static javax.measure.unit.NonSI.HOUR;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.Future;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.unit.SI;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.messaging.Connection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.MessageHandler;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherControlParameters;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherDriver;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.ral.values.CommodityProfile;
import org.flexiblepower.simulation.api.dishwasher.DishwasherSimulation;
import org.flexiblepower.simulation.dishwasher.DishwasherSimulationImpl.Config;
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
                                                                                                                 implements
                                                                                                                 DishwasherDriver,
                                                                                                                 DishwasherSimulation {

    private static final Logger logger = LoggerFactory.getLogger(DishwasherSimulationImpl.State.class);

    private final class RunProgram implements Runnable {
        private final String program;

        private RunProgram(String program) {
            this.program = program;
        }

        @Override
        public void run() {
            update(new State(true, context.currentTime(), context.currentTime(), program));
            future = null;
        }
    }

    @Meta.OCD
    interface Config {
        String resourceId();
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

        @Override
        public String toString() {
            return "State [isConnected=" + isConnected
                   + ", startTime="
                   + startTime
                   + ", latestStartTime="
                   + latestStartTime
                   + ", program="
                   + program
                   + "]";
        }
    }

    private FlexiblePowerContext context;

    @Reference
    public void setContext(FlexiblePowerContext context) {
        this.context = context;
    }

    private DishwasherWidget widget;
    private ServiceRegistration<Widget> widgetRegistration;

    @Activate
    public void activate(BundleContext context, Map<String, Object> properties) {
        setIdle();
        widget = new DishwasherWidget(this);
        widgetRegistration = context.registerService(Widget.class, widget, null);
        logger.info("Activated");
    }

    @Deactivate
    public void deactivate() {
        cancelJob();
        update(new State(false, null, null, null));

        widgetRegistration.unregister();
        logger.info("Deactivated");
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
    public synchronized void handleControlParameters(final DishwasherControlParameters resourceControlParameters) {
        logger.debug("Handle controlParameters: {}", resourceControlParameters);
        if (resourceControlParameters.getProgram().equals(currentState.getProgram())) {
            Measurable<Duration> diff = TimeUtil.difference(context.currentTime(),
                                                            resourceControlParameters.getStartTime());

            cancelJob();

            final String program = currentState.getProgram();
            logger.debug("Scheduling start over {}", diff);
            future = context.schedule(new RunProgram(program), diff);

        } else {
            logger.warn("Trying to start a different program (current={}, controlled={})",
                     currentState.getProgram(),
                     resourceControlParameters.getProgram());
        }
    }

    @Override
    public synchronized void setIdle() {
        cancelJob();
        update(new State(true, null, null, null));
    }

    public synchronized void setProgram(final String program, final Measurable<Duration> waitTime) {
        setProgram(program, TimeUtil.add(context.currentTime(), waitTime));
    }

    @Override
    public synchronized void setProgram(final String program, final Date latestStartTime) {
        cancelJob();

        update(new State(true, null, latestStartTime, program));
        final Measurable<Duration> diff = TimeUtil.difference(context.currentTime(), latestStartTime);
        future = context.schedule(new RunProgram(program), diff);
    }

    private void update(State state) {
        logger.trace("Updating state to: {}", state);
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
