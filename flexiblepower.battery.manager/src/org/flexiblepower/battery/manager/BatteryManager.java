package org.flexiblepower.battery.manager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Money;
import javax.measure.quantity.Power;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.flexiblepower.battery.manager.BatteryManager.Config;
import org.flexiblepower.efi.BufferResourceManager;
import org.flexiblepower.efi.buffer.Actuator;
import org.flexiblepower.efi.buffer.BufferAllocation;
import org.flexiblepower.efi.buffer.BufferRegistration;
import org.flexiblepower.efi.buffer.BufferRegistration.ActuatorCapabilities;
import org.flexiblepower.efi.buffer.BufferStateUpdate;
import org.flexiblepower.efi.buffer.BufferStateUpdate.ActuatorUpdate;
import org.flexiblepower.efi.buffer.BufferSystemDescription;
import org.flexiblepower.efi.buffer.BufferUpdate;
import org.flexiblepower.efi.buffer.LeakageFunction;
import org.flexiblepower.efi.buffer.RunningMode;
import org.flexiblepower.efi.buffer.RunningMode.RunningModeRangeElement;
import org.flexiblepower.efi.buffer.Transition;
import org.flexiblepower.efi.timeshifter.SequentialProfile;
import org.flexiblepower.efi.timeshifter.TimeShifterUpdate;
import org.flexiblepower.efi.util.Timer;
import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationProvider;
import org.flexiblepower.rai.comm.Allocation;
import org.flexiblepower.rai.comm.ResourceMessage;
import org.flexiblepower.rai.values.Commodity;
import org.flexiblepower.rai.values.Commodity.Measurements;
import org.flexiblepower.rai.values.CommodityForecast;
import org.flexiblepower.rai.values.ConstraintList;
import org.flexiblepower.rai.values.EnergyProfile;
import org.flexiblepower.rai.values.UncertainMeasure;
import org.flexiblepower.ral.ResourceDriver;
import org.flexiblepower.ral.ResourceManager;
import org.flexiblepower.ral.drivers.battery.BatteryControlParameters;
import org.flexiblepower.ral.drivers.battery.BatteryMode;
import org.flexiblepower.ral.drivers.battery.BatteryState;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.time.TimeService;
import org.flexiblepower.time.TimeUtil;
import org.osgi.service.component.annotations.Activate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = ResourceManager.class)
public class BatteryManager extends
                           AbstractResourceManager<BatteryState, BatteryControlParameters> implements
                                                                                          BufferResourceManager {
    private static final Logger log = LoggerFactory.getLogger(BatteryManager.class);

    @Meta.OCD
    interface Config {
    }

    private final BatteryState lastBatteryState = null;

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    private BatteryState currentState;
    private Date changedState;
    private BufferUpdate currentUpdate;
    private BufferAllocation currentAllocation;
    private Measurable<Duration> allocationDelay;

    double chargeSpeedGlobal = Double.MAX_VALUE;
    double dischargeSpeedGlobal = Double.MIN_VALUE;

    final int CHARGING_MODE = 1;
    final int IDLE_MODE = 2;
    final int DISCHARGING_MODE = 3;

    @Override
    protected List<? extends ResourceMessage> startRegistration(BatteryState state) {
        currentState = state;
        changedState = timeService.getTime();

        Actuator batteryActuator = getBatteryActuator(1);
        ActuatorCapabilities actuatorCapability = new ActuatorCapabilities(batteryActuator.getId(),
                                                                           "Battery",
                                                                           Commodity.Set.onlyElectricity);
        Set<ActuatorCapabilities> actuatorCapabilities = new HashSet<BufferRegistration.ActuatorCapabilities>();
        actuatorCapabilities.add(actuatorCapability);
        Unit<?> fillLevelUnit = NonSI.KWH;
        allocationDelay = Measure.valueOf(0, SI.SECOND);
        BufferRegistration reg = new BufferRegistration(null,
                                                        changedState,
                                                        allocationDelay,
                                                        "Battery level",
                                                        fillLevelUnit,
                                                        actuatorCapabilities);

        List<Actuator> actuators = Arrays.asList(batteryActuator);
        double lowerBound = 0;
        double upperBound = 6;
        double fillingSpeed = 0.0000001;
        LeakageFunction bufferLeakage = LeakageFunction.create().add(lowerBound, upperBound, fillingSpeed).build();
        Date timestamp = changedState;
        Date validFromDescr = changedState;
        BufferSystemDescription sysDescr = new BufferSystemDescription(null,
                                                                       timestamp,
                                                                       validFromDescr,
                                                                       allocationDelay,
                                                                       actuators,
                                                                       bufferLeakage);

        Date validFromUpdate = changedState;
        Measurable<Duration> allocationDelay = Measure.valueOf(0, SI.SECOND);

        ActuatorUpdate actuatorUpdate = new ActuatorUpdate(batteryActuator.getId(), IDLE_MODE, null);
        Set<ActuatorUpdate> currentRunningMode;
        Measure<Double, ?> currentFillLevel;
        BufferStateUpdate update = new BufferStateUpdate(null,
                                                         changedState,
                                                         validFromUpdate,
                                                         allocationDelay,
                                                         currentFillLevel,
                                                         currentRunningMode);

        return Arrays.asList(reg, sysDescr, update);
    }

    private Actuator getBatteryActuator(int actuatorId) {
        // transitions
        Transition toChargingTransition = makeTransition(CHARGING_MODE);
        Transition toIdleTransition = makeTransition(IDLE_MODE);
        Transition toDisChargingTransition = makeTransition(DISCHARGING_MODE);

        // running battery modes
        RunningMode chargingRunningMode = makeRunningMode(CHARGING_MODE,
                                                          "charging", makeRunningModeChargingElements(),
                                                          toIdleTransition, toDisChargingTransition);
        RunningMode idleRunningMode = makeRunningMode(IDLE_MODE,
                                                      "idle",
                                                      makeRunningModeIdleElements(),
                                                      toChargingTransition,
                                                      toDisChargingTransition);
        RunningMode dischargingRunningMode = makeRunningMode(DISCHARGING_MODE,
                                                             "discharging", makeRunningModeDisChargingElements(),
                                                             toIdleTransition, toChargingTransition);
        List<RunningMode> runningModes = Arrays.asList(chargingRunningMode, idleRunningMode, dischargingRunningMode);

        // battery actuator
        Collection<Timer> timerList = null;
        return new Actuator(actuatorId, timerList, runningModes);
    }

    private Transition makeTransition(int transitionId) {
        Set<Timer> startTimers = null;
        Set<Timer> blockingTimers = null;
        Measurable<Money> transitionCosts = Measure.valueOf(0, NonSI.EUROCENT);
        Measurable<Duration> transitionTime = Measure.valueOf(0, SI.SECOND);
        return new Transition(transitionId,
                              startTimers,
                              blockingTimers,
                              transitionCosts,
                              transitionTime);
    }

    private RunningMode makeRunningMode(int id,
                                        String name,
                                        RunningModeRangeElement[] runningModeRangeElements,
                                        Transition... transitions) {
        Set<Transition> transistionSet = new HashSet<Transition>();
        for (Transition transition : transitions) {
            transistionSet.add(transition);
        }

        return new RunningMode(id, name, transistionSet, runningModeRangeElements);
    }

    private RunningModeRangeElement[] makeRunningModeChargingElements() {
        List<RunningModeRangeElement> rows = new ArrayList<RunningModeRangeElement>();
        Measurable<Money> runningCostsPerSecond = Measure.valueOf(0, NonSI.EUROCENT);

        Measurements commodityConsumptionRow0 = new Measurements(Measure.valueOf(1460, SI.WATT), null);
        rows.add(new RunningModeRangeElement(0, 5, 0.0003968, commodityConsumptionRow0, runningCostsPerSecond));

        Measurements commodityConsumptionRow1 = new Measurements(Measure.valueOf(1050, SI.WATT), null);
        rows.add(new RunningModeRangeElement(5, 6, 0.0002778, commodityConsumptionRow1, runningCostsPerSecond));
        return (RunningModeRangeElement[]) rows.toArray();
    }

    private RunningModeRangeElement[] makeRunningModeIdleElements() {
        List<RunningModeRangeElement> rows = new ArrayList<RunningModeRangeElement>();
        Measurable<Money> runningCostsPerSecond = Measure.valueOf(0, NonSI.EUROCENT);

        Measurements commodityConsumptionRow0 = new Measurements(Measure.valueOf(0, SI.WATT), null);
        rows.add(new RunningModeRangeElement(0, 5, 0, commodityConsumptionRow0, runningCostsPerSecond));

        Measurements commodityConsumptionRow1 = new Measurements(Measure.valueOf(0, SI.WATT), null);
        rows.add(new RunningModeRangeElement(5, 6, 0, commodityConsumptionRow1, runningCostsPerSecond));
        return (RunningModeRangeElement[]) rows.toArray();
    }

    private RunningModeRangeElement[] makeRunningModeDisChargingElements() {
        List<RunningModeRangeElement> rows = new ArrayList<RunningModeRangeElement>();
        Measurable<Money> runningCostsPerSecond = Measure.valueOf(0, NonSI.EUROCENT);

        Measurements commodityConsumptionRow0 = new Measurements(Measure.valueOf(-1400, SI.WATT), null);
        rows.add(new RunningModeRangeElement(0, 5, -0.0003968, commodityConsumptionRow0, runningCostsPerSecond));

        Measurements commodityConsumptionRow1 = new Measurements(Measure.valueOf(-1400, SI.WATT), null);
        rows.add(new RunningModeRangeElement(5, 6, -0.0003968, commodityConsumptionRow1, runningCostsPerSecond));
        return (RunningModeRangeElement[]) rows.toArray();
    }

    @Override
    protected List<? extends ResourceMessage> updatedState(BatteryState state) {
        if (state.equals(currentState)) {
            return Collections.emptyList();
        } else {
            currentState = state;
            changedState = timeService.getTime();

            // BufferStateUpdate update = new createControlSpace(state);

            return Arrays.asList(update);
        }
    }

    @Override
    protected BatteryControlParameters receivedAllocation(ResourceMessage message) {
        // TODO Auto-generated method stub
        return null;
    }

    // -----------------------------------------

    @Override
    public void consume(ObservationProvider<? extends BatteryState> source, Observation<? extends
                                                                                        BatteryState> observation) {
        logger.debug("Observation received from " + source + ": " + observation.getValue());

        BatteryState state = observation.getValue();

        lastBatteryState = state;

        ConstraintList<Power> chargeSpeed = ConstraintList.create(WATT).addSingle(state.getChargeSpeed()).build();
        ConstraintList<Power> dischargeSpeed = ConstraintList.create(WATT).addSingle(state.getDischargeSpeed()).build();
        chargeSpeedGlobal = state.getChargeSpeed().doubleValue(WATT);
        dischargeSpeedGlobal = -1 *
                state.getDischargeSpeed().doubleValue(WATT);

        publish(new StorageControlSpace(config.resourceId(),
                                        timeService.getTime(),
                                        TimeUtil.add(timeService.getTime(),
                                                     expirationTime),
                                                     TimeUtil.add(timeService.getTime(), expirationTime),
                                                     state.getTotalCapacity(),
                                                     (float)
                                                     state.getStateOfCharge(),
                                                     chargeSpeed,
                                                     dischargeSpeed,
                                                     state.getSelfDischargeSpeed(),
                                                     (float)
                                                     state.getChargeEfficiency(),
                                                     (float) state.getDischargeEfficiency(),
                                                     state.getMinimumOnTime(),
                                                     state.getMinimumOffTime(),
                                                     null,
                                                     null));

    }

    @Activate
    public void init(Map<String, ?> properties) {
        config = Configurable.createConfigurable(Config.class,
                                                 properties);
        expirationTime = Measure.valueOf(config.expirationTime(), SECOND);
    }

    @Override
    public void handleAllocation(Allocation allocation) {

        ResourceDriver<? extends BatteryState, ? super BatteryControlParameters> driver = getDriver();
        if (allocation !=
                null && driver != null) {
            EnergyProfile energyProfile = allocation.getEnergyProfile();
            Measurable<Energy> energyValue = energyProfile.getElementForOffset(Measure.valueOf(0, SECOND)).getEnergy();
            final double energy =
                    energyValue.doubleValue(JOULE);
            final double power = energyProfile.getElementForOffset(Measure.valueOf(0,
                                                                                   SECOND))
                                                                                   .getAveragePower()
                                                                                   .doubleValue(WATT);
            logger.debug("----------------Setting energy level to " + power);
            logger.debug("----------------Setting energy level to " + energyProfile.toString());
            driver.setControlParameters(new BatteryControlParameters() {

                @Override
                public BatteryMode getMode() {
                    if (power <= dischargeSpeedGlobal && (lastBatteryState == null ||
                            lastBatteryState.getStateOfCharge() > 0)) {
                        return BatteryMode.DISCHARGE;
                    } else if (energy >= chargeSpeedGlobal
                            && (lastBatteryState == null || lastBatteryState.getStateOfCharge() < 1)) {
                        return BatteryMode.CHARGE;
                    } else {
                        return BatteryMode.IDLE;
                    }
                }
            });
        } else {
            logger.info("Received Allocation Null");
        }
    }

    private TimeShifterUpdate createControlSpace(DishwasherState info) {
        // Get values from driver
        String program = "";
        Date startTime = timeService.getTime();

        if (info.getStartTime() == null) {
            return null;
        }

        logger.debug("Program selected: " + info.getProgram());
        program = info.getProgram();
        startTime = info.getStartTime();

        // Create energy Profile
        // Unit<Power> flowUnit = Commodity.Electricity.instance.getFlowUnit();
        UncertainMeasure<Power> energy = new UncertainMeasure<Power>(1000, SI.WATT);
        UncertainMeasure<Duration> duration = new UncertainMeasure<Duration>(1, NonSI.HOUR);

        // Set Energy Profile
        if (program == "Energy Save") {
        } else if (program == "Sensor Wash") {
            duration = new UncertainMeasure<Duration>(2, NonSI.HOUR);
        } else {
            energy = new UncertainMeasure<Power>(2000, SI.WATT);
            duration = new UncertainMeasure<Duration>(2, NonSI.HOUR);
        }

        // Set Start and Stop Time
        Date startBefore = new Date(startTime.getTime());

        CommodityForecast<Energy, Power> forecast = CommodityForecast.create(Commodity.ELECTRICITY)
                                                                     .add(duration, energy)
                                                                     .build();
        return new TimeShifterUpdate(null,
                                     changedState,
                                     changedState,
                                     allocationDelay,
                                     startBefore,
                                     Arrays.asList(new SequentialProfile(0,
                                                                         duration,
                                                                         new CommodityForecast.Map(forecast, null))));
    }

}
