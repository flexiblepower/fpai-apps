package org.flexiblepower.simulation.battery;

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
import javax.measure.unit.SI;

import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.ral.drivers.battery.BatteryControlParameters;
import org.flexiblepower.ral.drivers.battery.BatteryDriver;
import org.flexiblepower.ral.drivers.battery.BatteryMode;
import org.flexiblepower.ral.drivers.battery.BatteryState;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.simulation.battery.BatterySimulation.Config;
import org.flexiblepower.time.TimeService;
import org.flexiblepower.ui.Widget;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Modified;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

/**
 * TODO Uit BatteryState weghalen van de charge en discharge efficiency
 *
 * @author waaijbdvd
 *
 */
@Component(designateFactory = Config.class, provide = Endpoint.class, immediate = true)
public class BatterySimulation extends AbstractResourceDriver<BatteryState, BatteryControlParameters> implements
                                                                                                     BatteryDriver,
                                                                                                     Runnable {
    interface Config {
        @Meta.AD(deflt = "5", description = "Interval between state updates [s]")
        long updateInterval();

        @Meta.AD(deflt = "1", description = "Total capacity [kWh]")
        double totalCapacity();

        @Meta.AD(deflt = "0.5", description = "Initial state of charge (% from 0 to 1)")
        double initialStateOfCharge();

        @Meta.AD(deflt = "1500", description = "Charge power [W]")
        long chargePower();

        @Meta.AD(deflt = "1500", description = "Discharge power [W]")
        long dischargePower();

        @Meta.AD(deflt = "0.9", description = "Charge efficiency (% from 0 to 1)")
        double chargeEfficiency();

        @Meta.AD(deflt = "0.9", description = "Discharge efficiency (% from 0 to 1)")
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
            return totalCapacityInKWh;
        }

        @Override
        public Measurable<Power> getChargeSpeed() {
            return chargeSpeedInWatt;
        }

        @Override
        public Measurable<Power> getDischargeSpeed() {
            return dischargeSpeedInWatt;
        }

        @Override
        public Measurable<Power> getSelfDischargeSpeed() {
            return selfDischargeSpeedInWatt;
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
            return minTimeOn;
        }

        @Override
        public Measurable<Duration> getMinimumOffTime() {
            return minTimeOff;
        }

        @Override
        public double getStateOfCharge() {
            return stateOfCharge;
        }

        @Override
        public BatteryMode getCurrentMode() {
            return mode;
        }

        @Override
        public String toString() {
            return "State [stateOfCharge=" + stateOfCharge + ", mode=" + mode + "]";
        }
    }

    private static final Logger log = LoggerFactory.getLogger(BatterySimulation.class);
    @SuppressWarnings("unchecked")
    private Measurable<Power> dischargeSpeedInWatt; // Watt
    private Measurable<Power> chargeSpeedInWatt; // Watt
    private Measurable<Power> selfDischargeSpeedInWatt; // Watt
    private Measurable<Energy> totalCapacityInKWh; // KWh
    private Measurable<Duration> minTimeOn; // seconden
    private Measurable<Duration> minTimeOff; // seconden

    private BatteryMode mode;
    private Measurable<Energy> stateOfChargeInJoule; // Joule
    private Date lastUpdatedTime;
    private Config configuration;

    private ScheduledExecutorService scheduler;
    private ServiceRegistration<Widget> widgetRegistration;

    private BatteryWidget widget;

    @Activate
    public void activate(BundleContext context, Map<String, Object> properties) {
        try {
            configuration = Configurable.createConfigurable(Config.class, properties);

            totalCapacityInKWh = Measure.valueOf(configuration.totalCapacity(), KWH);
            chargeSpeedInWatt = Measure.valueOf(configuration.chargePower(), WATT);
            dischargeSpeedInWatt = Measure.valueOf(configuration.dischargePower(), WATT);
            selfDischargeSpeedInWatt = Measure.valueOf(configuration.selfDischargePower(), WATT);
            stateOfChargeInJoule = Measure.valueOf(totalCapacityInKWh.doubleValue(JOULE) * configuration.initialStateOfCharge(),
                                                   JOULE);
            minTimeOn = Measure.valueOf(0, SI.SECOND);
            minTimeOff = Measure.valueOf(0, SI.SECOND);

            mode = BatteryMode.IDLE;

            publishState(new State(getRelativeStateOfCharge(), mode));

            scheduledFuture = scheduler.scheduleAtFixedRate(this, 0, configuration.updateInterval(), TimeUnit.SECONDS);

            widget = new BatteryWidget(this);
            widgetRegistration = context.registerService(Widget.class, widget, null);
        } catch (RuntimeException ex) {
            logger.error("Error during initialization of the battery simulation: " + ex.getMessage(), ex);
            deactivate();
            throw ex;
        }
    }

    @Deactivate
    public void deactivate() {
        if (widgetRegistration != null) {
            widgetRegistration.unregister();
            widgetRegistration = null;
        }
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
    }

    @Modified
    public void modify(BundleContext context, Map<String, Object> properties) {
        try {
            configuration = Configurable.createConfigurable(Config.class, properties);

            totalCapacityInKWh = Measure.valueOf(configuration.totalCapacity(), KWH);
            chargeSpeedInWatt = Measure.valueOf(configuration.chargePower(), WATT);
            dischargeSpeedInWatt = Measure.valueOf(configuration.dischargePower(), WATT);
            selfDischargeSpeedInWatt = Measure.valueOf(configuration.selfDischargePower(), WATT);
            stateOfChargeInJoule = Measure.valueOf(totalCapacityInKWh.doubleValue(JOULE) * configuration.initialStateOfCharge(),
                                                   JOULE);
            minTimeOn = Measure.valueOf(2, SI.SECOND);
            minTimeOff = Measure.valueOf(2, SI.SECOND);
            mode = BatteryMode.IDLE;
        } catch (RuntimeException ex) {
            logger.error("Error during initialization of the battery simulation: " + ex.getMessage(), ex);
            deactivate();
            throw ex;
        }
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
        lastUpdatedTime = timeService.getTime();
    }

    private ScheduledFuture<?> scheduledFuture;

    @Reference
    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public synchronized void run() {
        Date currentTime = timeService.getTime();
        double durationSinceLastUpdate = (currentTime.getTime() - lastUpdatedTime.getTime()) / 1000.0; // in seconds
        lastUpdatedTime = currentTime;
        double amountOfChargeInWatt = 0;

        logger.debug("Battery simulation step. Mode={} Timestep={}s", mode, durationSinceLastUpdate);
        if (durationSinceLastUpdate > 0) {
            switch (mode) {
            case IDLE:
                amountOfChargeInWatt = 0;
                break;
            case CHARGE:
                amountOfChargeInWatt = chargeSpeedInWatt.doubleValue(WATT);
                break;
            case DISCHARGE:
                amountOfChargeInWatt = -dischargeSpeedInWatt.doubleValue(WATT);
                break;
            default:
                throw new AssertionError();
            }
            // always also self discharge
            amountOfChargeInWatt = amountOfChargeInWatt - selfDischargeSpeedInWatt.doubleValue(WATT);

            double newStateOfChargeJoulesValue = stateOfChargeInJoule.doubleValue(JOULE) + (amountOfChargeInWatt * durationSinceLastUpdate);
            // check id stateOfCharge is not outside limits of the battery
            if (newStateOfChargeJoulesValue < 0) {
                newStateOfChargeJoulesValue = 0;
                // indicate that battery has stopped discharging
                mode = BatteryMode.IDLE;
            } else {
                double totalCapacityInJoules = totalCapacityInKWh.doubleValue(JOULE);
                if (newStateOfChargeJoulesValue > totalCapacityInJoules) {
                    newStateOfChargeJoulesValue = totalCapacityInJoules;
                    // indicate that battery has stopped discharging
                    mode = BatteryMode.IDLE;
                }
            }

            stateOfChargeInJoule = Measure.valueOf(newStateOfChargeJoulesValue, JOULE);
            log.debug("durationSinceLastUpdate=" + durationSinceLastUpdate
                      + " stateOfCharge(JOULE)=" + stateOfChargeInJoule.doubleValue(JOULE)
                      + " amountOfCharge=" + amountOfChargeInWatt
                      + " newStateOfChargeInJoulesValue="
                      + newStateOfChargeJoulesValue
                      + " stateOfChargeInJoule="
                      + stateOfChargeInJoule);

            // convert stateOfChargeInJoules into a relevative state of charge
            State state = new State(getRelativeStateOfCharge(), mode);
            logger.debug("Publishing state {}", state);
            publishState(state);
        }
    }

    @Override
    protected void handleControlParameters(BatteryControlParameters controlParameters) {
        mode = controlParameters.getMode();
    }

    private double getRelativeStateOfCharge() {
        return stateOfChargeInJoule.doubleValue(JOULE) / totalCapacityInKWh.doubleValue(JOULE);
    }

    protected State getCurrentState() {
        return new State(getRelativeStateOfCharge(), mode);
    }
}
