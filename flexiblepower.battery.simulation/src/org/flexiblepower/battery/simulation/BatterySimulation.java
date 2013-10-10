package org.flexiblepower.battery.simulation;

import static javax.measure.unit.NonSI.KWH;
import static javax.measure.unit.SI.JOULE;
import static javax.measure.unit.SI.WATT;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;

import org.flexiblepower.battery.simulation.BatterySimulation.Config;
import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationProviderRegistrationHelper;
import org.flexiblepower.ral.ResourceDriver;
import org.flexiblepower.ral.drivers.battery.BatteryControlParameters;
import org.flexiblepower.ral.drivers.battery.BatteryDriver;
import org.flexiblepower.ral.drivers.battery.BatteryMode;
import org.flexiblepower.ral.drivers.battery.BatteryState;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.time.TimeService;
import org.flexiblepower.time.TimeUtil;
import org.flexiblepower.ui.Widget;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = ResourceDriver.class, immediate = true)
public class BatterySimulation extends AbstractResourceDriver<BatteryState, BatteryControlParameters> implements
                                                                                                     BatteryDriver,
                                                                                                     Runnable {
    interface Config {
        @Meta.AD(deflt = "battery", description = "Resource identifier")
        String resourceId();

        @Meta.AD(deflt = "5", description = "Interval between state updates [s]")
        long updateInterval();

        @Meta.AD(deflt = "1", description = "Total capacity [kWh]")
        long totalCapacity();

        @Meta.AD(deflt = "0.5", description = "Initial state of charge (% from 0 to 1)")
        double initialStateOfCharge();

        @Meta.AD(deflt = "1500", description = "Charge power [W]")
        long chargePower();

        @Meta.AD(deflt = "1500", description = "Discharge power [W]")
        long dischargePower();

        @Meta.AD(deflt = "0.9", description = "Charge efficiency (% from 0 to 1)")
        double chargeEfficiency();

        @Meta.AD(deflt = "0.8", description = "Discharge efficiency (% from 0 to 1)")
        double dischargeEfficiency();

        @Meta.AD(deflt = "50", description = "Self discharge power [W]")
        long selfDischargePower();
    }

    class State implements BatteryState {
        private final double stateOfCharge;
        private final BatteryMode mode;

        public State(double stateOfCharge, BatteryMode mode) {
            this.stateOfCharge = stateOfCharge;
            this.mode = mode;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public Measurable<Energy> getTotalCapacity() {
            return totalCapacity;
        }

        @Override
        public Measurable<Power> getChargeSpeed() {
            return chargeSpeed;
        }

        @Override
        public Measurable<Power> getDischargeSpeed() {
            return dischargeSpeed;
        }

        @Override
        public Measurable<Power> getSelfDischargeSpeed() {
            return selfDischargeSpeed;
        }

        @Override
        public double getChargeEfficiency() {
            return configuration.chargeEfficiency();
        }

        @Override
        public double getDischargeEfficiency() {
            return configuration.dischargeEfficiency();
        }

        @Override
        public Measurable<Duration> getMinimumOnTime() {
            return TimeUtil.ZERO;
        }

        @Override
        public Measurable<Duration> getMinimumOffTime() {
            return TimeUtil.ZERO;
        }

        @Override
        public double getStateOfCharge() {
            return stateOfCharge;
        }

        @Override
        public BatteryMode getCurrentMode() {
            return mode;
        }

    }

    private Measurable<Power> dischargeSpeed;
    private Measurable<Power> chargeSpeed;
    private Measurable<Power> selfDischargeSpeed;
    private Measurable<Energy> totalCapacity;

    private BatteryMode mode;
    private Measurable<Energy> stateOfCharge;
    private Date startTime;
    private Config configuration;

    private ServiceRegistration<?> observationProviderRegistration;
    private ScheduledExecutorService scheduler;
    private ServiceRegistration<Widget> widgetRegistration;

    private BatteryWidget widget;

    @Activate
    public void init(BundleContext bundleContext, Map<String, Object> properties) {
        configuration = Configurable.createConfigurable(Config.class, properties);

        totalCapacity = Measure.valueOf(configuration.totalCapacity(), KWH);
        chargeSpeed = Measure.valueOf(configuration.chargePower(), WATT);
        dischargeSpeed = Measure.valueOf(configuration.dischargePower(), WATT);
        selfDischargeSpeed = Measure.valueOf(configuration.selfDischargePower(), WATT);
        stateOfCharge = Measure.valueOf(totalCapacity.doubleValue(JOULE) * configuration.initialStateOfCharge(), JOULE);
        mode = BatteryMode.IDLE;

        String applianceId = (String) properties.get("applianceId");
        observationProviderRegistration = new ObservationProviderRegistrationHelper(this).observationType(BatteryState.class)
                                                                                         .observationOf(applianceId)
                                                                                         .observedBy(applianceId)
                                                                                         .register();

        publish(new Observation<BatteryState>(timeService.getTime(), new State(getRelativeStateOfCharge(), mode)));

        scheduledFuture = scheduler.scheduleAtFixedRate(this, 0, configuration.updateInterval(), TimeUnit.SECONDS);

        widget = new BatteryWidget(this);
        widgetRegistration = bundleContext.registerService(Widget.class, widget, null);
    }

    @Deactivate
    public void deactivate() {
        widgetRegistration.unregister();
        observationProviderRegistration.unregister();
        scheduledFuture.cancel(false);
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
        startTime = timeService.getTime();
    }

    private ScheduledFuture<?> scheduledFuture;

    @Reference
    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public synchronized void run() {
        logger.debug("Simulated battery " + configuration.resourceId() + " has mode " + mode);

        Date currentTime = timeService.getTime();
        double duration = (currentTime.getTime() - startTime.getTime()) / 1000.0; // in seconds
        double amountOfCharge = 0; // in joules

        if (duration > 0) {
            switch (mode) {
            case IDLE:
                // TODO doesn't the self discharge always apply?
                amountOfCharge = -selfDischargeSpeed.doubleValue(WATT);
                break;
            case CHARGE:
                amountOfCharge = chargeSpeed.doubleValue(WATT);
                break;
            case DISCHARGE:
                amountOfCharge = -dischargeSpeed.doubleValue(WATT);
                break;
            default:
                throw new AssertionError();
            }

            double stateOfChargeInJoules = stateOfCharge.doubleValue(JOULE) + (amountOfCharge * duration);
            if (stateOfChargeInJoules < 0) {
                stateOfChargeInJoules = 0;
            } else {
                double totalCapacityInJoules = totalCapacity.doubleValue(JOULE);
                if (stateOfChargeInJoules > totalCapacityInJoules) {
                    stateOfChargeInJoules = totalCapacityInJoules;
                }
            }

            stateOfCharge = Measure.valueOf(stateOfChargeInJoules, JOULE);

            publish(new Observation<BatteryState>(currentTime, new State(getRelativeStateOfCharge(), mode)));
        }
        startTime = currentTime;
    }

    @Override
    public void setControlParameters(BatteryControlParameters resourceControlParameters) {
        mode = resourceControlParameters.getMode();
        run();
    }

    private double getRelativeStateOfCharge() {
        return stateOfCharge.doubleValue(JOULE) / totalCapacity.doubleValue(JOULE);
    }

    protected State getCurrentState() {
        return new State(getRelativeStateOfCharge(), mode);
    }
}
