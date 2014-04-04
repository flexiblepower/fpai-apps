package org.flexiblepower.heatpump.simulation;

import java.text.DecimalFormat;
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

import org.flexiblepower.heatpump.simulation.HeatPumpSimulation.Config;
import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationProvider;
import org.flexiblepower.rai.Allocation;
import org.flexiblepower.rai.BufferControlSpace;
import org.flexiblepower.rai.values.ConstraintList;
import org.flexiblepower.ral.ResourceManager;
import org.flexiblepower.ral.drivers.battery.BatteryControlParameters;
import org.flexiblepower.ral.drivers.battery.BatteryState;
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.time.TimeService;
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

@Component(designateFactory = Config.class, provide = { ResourceManager.class })
public class HeatPumpSimulation extends
                               AbstractResourceManager<BufferControlSpace, BatteryState, BatteryControlParameters> implements
                                                                                                                  Runnable {

    public HeatPumpSimulation() {
        super(null, BufferControlSpace.class);
    }

    interface Config {
        @Meta.AD(deflt = "heatpump", description = "Resource identifier")
        String resourceId();

        @Meta.AD(deflt = "1000", description = "Evergy that 'leaks' out of the living room in watts")
        double leakage();

        @Meta.AD(deflt = "3000", description = "Electrical power in watts the heatpump consumes")
        double powerInput();

        @Meta.AD(deflt = "3", description = "COP of heatpump")
        double cop();

        @Meta.AD(deflt = "900000", description = "Energy required to heat the room 1 degree celcius in joules")
        double energyForHeatIncrease();

        @Meta.AD(deflt = "18", description = "Initial temperature in degrees celcius")
        double initialTemperature();

        @Meta.AD(deflt = "30", description = "Minimum on period in seconds")
        double minOn();

        @Meta.AD(deflt = "30", description = "Minimum off period in seconds")
        double minOff();
    }

    private static final Logger log = LoggerFactory.getLogger(HeatPumpSimulation.class);
    private static final DecimalFormat df = new DecimalFormat("#.##");

    private Config config;
    private ScheduledExecutorService scheduler;
    private TimeService timeService;
    private long lastUpdate;
    private long minOnUntil = -1;
    private long minOffUntil = -1;

    private double currentTemperature;
    private double setTemperature;
    private double temperatureRange;
    private double currentDemandWatt;

    private ScheduledFuture<?> scheduledFuture;
    private HeatPumpSimulationWidget widget;
    private ServiceRegistration<Widget> widgetRegistration;

    @Activate
    public void activate(BundleContext context, Map<String, Object> properties) {
        try {
            config = Configurable.createConfigurable(Config.class, properties);
            currentTemperature = config.initialTemperature();
            setTemperature = config.initialTemperature();
            temperatureRange = 0.5;
            currentDemandWatt = 0;
            lastUpdate = timeService.getCurrentTimeMillis();
            scheduledFuture = scheduler.scheduleAtFixedRate(this, 0, 1, TimeUnit.SECONDS);
            widget = new HeatPumpSimulationWidget(this);
            widgetRegistration = context.registerService(Widget.class, widget, null);

        } catch (Exception e) {
            log.error("Error while initializing HeatPumpSimulatios", e);
        }
    }

    @Deactivate
    public void deactivate() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
        if (widgetRegistration != null) {
            widgetRegistration.unregister();
            widgetRegistration = null;
        }
    }

    @Reference
    public void setScheduledExecutorService(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    @Override
    public synchronized void run() {
        long now = timeService.getCurrentTimeMillis();
        double seconds = (now - lastUpdate) / 1000d;

        // Update heatpump
        if (minOnUntil > 0) {
            log.debug("in must-run for another " + (minOnUntil - now) + " ms");
            // in minOn
            if (minOnUntil <= now) {
                minOnUntil = -1;
            }
            currentDemandWatt = config.powerInput();
        } else if (minOffUntil > 0) {
            log.debug("in must-off for another " + (minOnUntil - now) + " ms");
            // in minOn
            if (minOffUntil <= now) {
                minOffUntil = -1;
            }
            currentDemandWatt = 0;
        }

        temperatureRange = Math.min(temperatureRange, 0.1);
        double minTemp = setTemperature - temperatureRange;
        double maxTemp = setTemperature + temperatureRange;
        double soc = (currentTemperature - minTemp) / (maxTemp - minTemp);
        if (soc < 0) {
            // turn on
            changePower(config.powerInput());
        } else if (soc > 1) {
            // turn off
            changePower(0);
        }
        soc = Math.max(Math.min(1, soc), 0);

        double heatpumpHeatJoules = currentDemandWatt * config.cop() * seconds;
        // Update room
        double leakedHeatJoules = config.leakage() * seconds;

        // Update temperature
        currentTemperature = currentTemperature + ((heatpumpHeatJoules - leakedHeatJoules) / config.energyForHeatIncrease());

        lastUpdate = now;

        log.debug("Power: " + currentDemandWatt
                  + " Watt, Current SOC: "
                  + soc
                  + ", current temperature: "
                  + currentTemperature);
        log.debug("Current mode: " + getMode());
        publish(createControlSpace(soc, new Date(now)));
    }

    private BufferControlSpace createControlSpace(double soc, Date now) {
        Date next = new Date(now.getTime() + 20000);
        Measurable<Energy> totalCapacity = Measure.valueOf(temperatureRange * 2 + config.energyForHeatIncrease(),
                                                           SI.JOULE);
        ConstraintList<Power> chargeSpeed = ConstraintList.create(SI.WATT).addSingle(config.powerInput()).build();
        Measurable<Power> selfDischarge = Measure.valueOf(config.leakage(), SI.WATT);
        Measurable<Duration> minOnPeriod = Measure.valueOf(config.minOn(), SI.SECOND);
        Measurable<Duration> minOffPeriod = Measure.valueOf(config.minOff(), SI.SECOND);
        return new BufferControlSpace(config.resourceId(),
                                      now,
                                      next,
                                      next,
                                      totalCapacity,
                                      soc,
                                      chargeSpeed,
                                      selfDischarge,
                                      minOnPeriod,
                                      minOffPeriod,
                                      null,
                                      null);
    }

    @Override
    public void consume(ObservationProvider<? extends BatteryState> source,
                        Observation<? extends BatteryState> observation) {
        // Ignore
    }

    @Override
    public void handleAllocation(Allocation allocation) {
        double powerWatt = allocation.getEnergyProfile().get(0).getAveragePower().doubleValue(SI.WATT);
        changePower(powerWatt);
    }

    private synchronized void changePower(double desiredDemandWatt) {
        long now = timeService.getCurrentTimeMillis();
        log.debug("Received allocation of " + desiredDemandWatt + " Watt");
        // Update heatpump
        if (minOnUntil > 0 || minOffUntil > 0) {
            // in must-run or must-off, not changing currentDemandWatt
        } else {
            if (currentDemandWatt < 0.001 && desiredDemandWatt > 0) {
                // turning on
                minOnUntil = now + ((long) config.minOn()) * 1000l;
                log.debug("Turning on");
            } else if (currentDemandWatt > 0 && desiredDemandWatt < 0.001) {
                // turning off
                log.debug("Turning off");
                minOffUntil = now + ((long) config.minOff()) * 1000l;
            }
            currentDemandWatt = desiredDemandWatt;
        }
    }

    public String getMode() {
        long now = timeService.getCurrentTimeMillis();
        if (minOnUntil > 0) {
            int secondsLeft = Math.max(0, (int) ((minOnUntil - now) / 1000));
            return "Must run (0:" + (secondsLeft < 10 ? "0" : "") + secondsLeft + ")";
        } else if (minOffUntil > 0) {
            int secondsLeft = Math.max(0, (int) ((minOffUntil - now) / 1000));
            return "Must off (0:" + (secondsLeft < 10 ? "0" : "") + secondsLeft + ")";
        } else if (currentDemandWatt > 0.0001) {
            return "Running (" + df.format(currentDemandWatt) + " watt)";
        } else {
            return "Off";
        }
    }

    public double getSetTemperature() {
        return setTemperature;
    }

    public void setSetTemperature(double setTemperature) {
        this.setTemperature = setTemperature;
    }

    public double getTemperatureRange() {
        return temperatureRange;
    }

    public void setTemperatureRange(double temperatureRange) {
        this.temperatureRange = temperatureRange;
    }

    public double getCurrentTemperature() {
        return currentTemperature;
    }

    public double getCurrentDemandWatt() {
        return currentDemandWatt;
    }

}
