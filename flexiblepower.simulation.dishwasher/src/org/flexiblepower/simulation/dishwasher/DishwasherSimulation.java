package org.flexiblepower.simulation.dishwasher;

import static javax.measure.unit.NonSI.HOUR;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.unit.SI;

import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.rai.values.CommodityProfile;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherControlParameters;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
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
public class DishwasherSimulation extends AbstractResourceDriver<DishwasherState, DishwasherControlParameters> implements
                                                                                                              org.flexiblepower.ral.drivers.dishwasher.DishwasherDriver
{
    private static final Logger log = LoggerFactory.getLogger(DishwasherSimulation.State.class);

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "true", description = "Device is connected to the driver")
        boolean isConnected();

        @Meta.AD(deflt = "2014-01-01 12:00", description = "Latest starttime of dishwasher (as given on device)")
        String latestStartTime();

        @Meta.AD(deflt = "No program selected", description = "Device is connected to the driver")
        String program();
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

    private Config configuration;

    private DishwasherWidget widget;

    private ServiceRegistration<Widget> widgetRegistration;

    @Activate
    public void activate(BundleContext context, Map<String, Object> properties) {
        log.info("Activated");
        configuration = Configurable.createConfigurable(Config.class, properties);

        Measurable<Duration> diff = getTimeDiff();

        if (runFuture != null && !runFuture.isDone()) {
            runFuture.cancel(false);
        }

        final boolean isConnected = configuration.isConnected();
        final Date startTime = timeService.getTime();
        final Date latestStartTime = getLatestTime();
        final String program = configuration.program();

        currentState = new State(isConnected,
                                 null,
                                 latestStartTime,
                                 program);
        publishState(currentState);

        runFuture = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                log.debug("Started by device");

                currentState = new State(isConnected,
                                         startTime,
                                         latestStartTime,
                                         program);
                publishState(currentState);
            }
        },
                                       diff.longValue(SI.SECOND),
                                       TimeUnit.SECONDS);

        widget = new DishwasherWidget(this, timeService);
        widgetRegistration = context.registerService(Widget.class, widget, null);
    }

    @Deactivate
    public void deactivate() {
        log.info("Deactivated");
        widgetRegistration.unregister();
    }

    private Measurable<Duration> getTimeDiff() {
        Date current = timeService.getTime();
        Date latestTime = getLatestTime();
        return TimeUtil.difference(current, latestTime);
    }

    private Date getLatestTime() {
        Date latestTime = timeService.getTime();
        String datestring = null;
        try {
            datestring = configuration.latestStartTime();
            latestTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).parse(datestring);
        } catch (ParseException e) {
            log.error("Parse error, parsing date string to Date object: {}", datestring);
            e.printStackTrace();
        }
        return latestTime;
    }

    private volatile State currentState;

    public State getCurrentState() {
        return currentState;
    }

    private volatile Future<?> runFuture;

    @Override
    public void handleControlParameters(final DishwasherControlParameters resourceControlParameters) {
        if (resourceControlParameters.getProgram().equals(currentState.getProgram())) {
            Measurable<Duration> diff = TimeUtil.difference(timeService.getTime(),
                                                            resourceControlParameters.getStartTime());

            if (runFuture != null && !runFuture.isDone()) {
                runFuture.cancel(false);
            }

            final boolean isConnected = configuration.isConnected();
            final Date latestStartTime = resourceControlParameters.getStartTime();
            final String program = configuration.program();

            currentState = new State(isConnected,
                                     null,
                                     latestStartTime,
                                     program);

            publishState(currentState);
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    log.debug("Started by manager");
                    currentState = new State(isConnected,
                                             latestStartTime,
                                             latestStartTime,
                                             program);
                    publishState(currentState);
                }
            };
            runFuture = scheduler.schedule(runnable,
                                           diff.longValue(SI.SECOND),
                                           TimeUnit.SECONDS);

        }
    }
}
