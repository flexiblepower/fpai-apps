package org.flexiblepower.battery.manager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
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
import org.flexiblepower.efi.buffer.LeakageFunction;
import org.flexiblepower.efi.buffer.RunningMode;
import org.flexiblepower.efi.buffer.RunningMode.RunningModeRangeElement;
import org.flexiblepower.efi.buffer.Transition;
import org.flexiblepower.efi.util.Timer;
import org.flexiblepower.rai.comm.ResourceMessage;
import org.flexiblepower.rai.values.Commodity;
import org.flexiblepower.rai.values.Commodity.Measurements;
import org.flexiblepower.ral.ResourceManager;
import org.flexiblepower.ral.drivers.battery.BatteryControlParameters;
import org.flexiblepower.ral.drivers.battery.BatteryMode;
import org.flexiblepower.ral.drivers.battery.BatteryState;
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.time.TimeService;
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
    @SuppressWarnings("unchecked")
    private static final Unit<Energy> WH = (Unit<Energy>) SI.WATT.times(NonSI.HOUR); // Define WattHour (Wh)
    private static final int BATTERY_ACTUATOR_ID = 1;

    @Meta.OCD
    interface Config {
    }

    private BatteryState currentBatteryState;
    private Date changedStateTimestamp;
    private Actuator batteryActuator;

    @Override
    protected List<? extends ResourceMessage> startRegistration(BatteryState batteryState) {
        currentBatteryState = batteryState;
        changedStateTimestamp = timeService.getTime();

        // Buffer registration
        batteryActuator = makeBatteryActuator(BATTERY_ACTUATOR_ID);
        ActuatorCapabilities actuatorCapability = new ActuatorCapabilities(batteryActuator.getId(),
                                                                           "Battery",
                                                                           Commodity.Set.onlyElectricity);
        Set<ActuatorCapabilities> actuatorCapabilities = new HashSet<BufferRegistration.ActuatorCapabilities>();
        actuatorCapabilities.add(actuatorCapability);
        BufferRegistration reg = new BufferRegistration(null,
                                                        changedStateTimestamp,
                                                        toSeconds(0),
                                                        "Battery level",
                                                        WH, // WH
                                                        actuatorCapabilities);

        // Buffer system description
        double lowerBound = 0; // 0 Wh
        double upperBound = 6000; // 6 KWh
        double leakageSpeed = 0.0001; // 0.0001W/s
        LeakageFunction bufferLeakage = LeakageFunction.create().add(lowerBound, upperBound, leakageSpeed).build();
        BufferSystemDescription sysDescr = new BufferSystemDescription(null,
                                                                       changedStateTimestamp,
                                                                       changedStateTimestamp,
                                                                       toSeconds(0),
                                                                       Arrays.asList(batteryActuator),
                                                                       bufferLeakage);

        // Buffer state update, based on batteryState
        Measure<Double, Energy> currentFillLevel = getCurrentFillLevel(batteryState);
        Set<ActuatorUpdate> currentRunningMode = makeBatteryActuatorUpdateSet(batteryState.getCurrentMode());
        BufferStateUpdate update = new BufferStateUpdate(null,
                                                         changedStateTimestamp,
                                                         changedStateTimestamp,
                                                         toSeconds(0),
                                                         currentFillLevel,
                                                         currentRunningMode);

        return Arrays.asList(reg, sysDescr, update);
    }

    @Override
    protected List<? extends ResourceMessage> updatedState(BatteryState batteryState) {
        if (batteryState.equals(currentBatteryState)) {
            return Collections.emptyList();
        } else {
            currentBatteryState = batteryState;
            changedStateTimestamp = timeService.getTime();
            Date validFrom = changedStateTimestamp;
            Measurable<Duration> allocationDelay = toSeconds(0);
            Measure<Double, Energy> currentFillLevel = getCurrentFillLevel(batteryState);

            // convert BatteryState into runningMode
            Set<ActuatorUpdate> actuatorUpdateSet = makeBatteryActuatorUpdateSet(batteryState.getCurrentMode());
            BufferStateUpdate update = new BufferStateUpdate(null,
                                                             changedStateTimestamp,
                                                             validFrom,
                                                             allocationDelay,
                                                             currentFillLevel,
                                                             actuatorUpdateSet);
            return Arrays.asList(update);
        }
    }

    @Override
    protected BatteryControlParameters receivedAllocation(ResourceMessage message) {
        if (message instanceof BufferAllocation) {
            BufferAllocation bufferAllocation = (BufferAllocation) message;

            // waarom krijgen we ook de ControlSpaceUpdate mee?
            // hoe komen we aan de actuatorAllocations, daar is geen method voor.
            // hoe schedulen we een timer, als er getimde allocations zijn?
            BatteryControlParameters batteryControlParameters = new BatteryControlParameters() {
                @Override
                public BatteryMode getMode() {

                    return null;
                }
            };

            return batteryControlParameters;
        } else {
            log.warn("Unexpected resource (" + message.toString() + ") message received");
            return null;
        }
    }

    // ---------------------------- helper classes -----------------------------
    /**
     * make a Battery actuator with runningmodes for each BatteryMode, transitions between them and no timers.
     *
     * @param actuatorId
     * @return
     */
    private Actuator makeBatteryActuator(int actuatorId) {
        // transitions
        Transition toChargingTransition = makeTransition(0);
        Transition toIdleTransition = makeTransition(1);
        Transition toDisChargingTransition = makeTransition(2);

        // running battery modes
        RunningMode chargingRunningMode = makeRunningMode(BatteryMode.CHARGE,
                                                          "charging", makeRunningModeChargingElements(),
                                                          toIdleTransition, toDisChargingTransition);
        RunningMode idleRunningMode = makeRunningMode(BatteryMode.IDLE,
                                                      "idle",
                                                      makeRunningModeIdleElements(),
                                                      toChargingTransition,
                                                      toDisChargingTransition);
        RunningMode dischargingRunningMode = makeRunningMode(BatteryMode.DISCHARGE,
                                                             "discharging", makeRunningModeDisChargingElements(),
                                                             toIdleTransition, toChargingTransition);
        List<RunningMode> runningModes = Arrays.asList(chargingRunningMode, idleRunningMode, dischargingRunningMode);

        // this battery actuator has no timers.
        Collection<Timer> timerList = null;

        // return the actuator
        return new Actuator(actuatorId, timerList, runningModes);
    }

    /**
     * Make a transistion with no timers;
     *
     * @param transitionId
     * @return
     */
    private Transition makeTransition(int transitionId) {
        Set<Timer> startTimers = null;
        Set<Timer> blockingTimers = null;
        Measurable<Money> transitionCosts = toEurocent(0);
        Measurable<Duration> transitionTime = toSeconds(0);
        return new Transition(transitionId,
                              startTimers,
                              blockingTimers,
                              transitionCosts,
                              transitionTime);
    }

    /**
     * Make a runningMode based on the batteryMode with an optional number of transitions
     *
     * @param batteryMode
     * @param name
     * @param runningModeRangeElements
     * @param transitions
     * @return
     */
    private RunningMode makeRunningMode(BatteryMode batteryMode,
                                        String name,
                                        RunningModeRangeElement[] runningModeRangeElements,
                                        Transition... transitions) {
        Set<Transition> transistionSet = new HashSet<Transition>();
        for (Transition transition : transitions) {
            transistionSet.add(transition);
        }

        return new RunningMode(batteryMode.ordinal(), name, transistionSet, runningModeRangeElements);
    }

    /**
     * Helper class for the charging running mode elements
     *
     * @return
     */
    private RunningModeRangeElement[] makeRunningModeChargingElements() {
        List<RunningModeRangeElement> rows = new ArrayList<RunningModeRangeElement>();

        Measurements commodityConsumptionRow0 = new Measurements(toWatt(1460), null);
        rows.add(new RunningModeRangeElement(0, 5000, 0.3968, commodityConsumptionRow0, toEurocent(0)));
        Measurements commodityConsumptionRow1 = new Measurements(toWatt(1050), null);
        rows.add(new RunningModeRangeElement(5000, 6000, 0.2778, commodityConsumptionRow1, toEurocent(0)));

        return (RunningModeRangeElement[]) rows.toArray();
    }

    /**
     * Helper class for the idle running mode elements
     *
     * @return
     */
    private RunningModeRangeElement[] makeRunningModeIdleElements() {
        List<RunningModeRangeElement> rows = new ArrayList<RunningModeRangeElement>();

        Measurements commodityConsumptionRow0 = new Measurements(toWatt(0), null);
        rows.add(new RunningModeRangeElement(0, 5000, 0, commodityConsumptionRow0, toEurocent(0)));
        Measurements commodityConsumptionRow1 = new Measurements(toWatt(0), null);
        rows.add(new RunningModeRangeElement(5000, 6000, 0, commodityConsumptionRow1, toEurocent(0)));

        return (RunningModeRangeElement[]) rows.toArray();
    }

    /**
     * Helper class for the discharging running mode elements
     *
     * @return
     */
    private RunningModeRangeElement[] makeRunningModeDisChargingElements() {
        List<RunningModeRangeElement> rows = new ArrayList<RunningModeRangeElement>();

        Measurements commodityConsumptionRow0 = new Measurements(toWatt(-1400), null);
        rows.add(new RunningModeRangeElement(0, 5000, -0.3968, commodityConsumptionRow0, toEurocent(0)));
        Measurements commodityConsumptionRow1 = new Measurements(toWatt(-1400), null);
        rows.add(new RunningModeRangeElement(5000, 6000, -0.3968, commodityConsumptionRow1, toEurocent(0)));

        return (RunningModeRangeElement[]) rows.toArray();
    }

    /**
     * Create the battery actuator set, based on the battery mode
     *
     * @param batteryMode
     * @return
     */
    private Set<ActuatorUpdate> makeBatteryActuatorUpdateSet(BatteryMode batteryMode) {
        Set<ActuatorUpdate> runningModes = new HashSet<ActuatorUpdate>();
        ActuatorUpdate actuatorUpdate = new ActuatorUpdate(batteryActuator.getId(), batteryMode.ordinal(), null);
        runningModes.add(actuatorUpdate);
        return runningModes;
    }

    /**
     * current fill level is calculated by multiplying the total capacity with the state of charge.
     *
     * @param batteryState
     * @return
     */
    private Measure<Double, Energy> getCurrentFillLevel(BatteryState batteryState) {
        double stateOfCharge = batteryState.getStateOfCharge();
        Measurable<Energy> totalCapacity = batteryState.getTotalCapacity();
        return Measure.valueOf(stateOfCharge * totalCapacity.doubleValue(WH),
                               WH);
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    // ---------------- helper conversion methodes ------------------

    private Measure<Integer, Power> toWatt(int watt) {
        return Measure.valueOf(watt, SI.WATT);
    }

    private Measure<Integer, Money> toEurocent(int eurocent) {
        return Measure.valueOf(eurocent, NonSI.EUROCENT);
    }

    private Measure<Integer, Duration> toSeconds(int seconds) {
        return Measure.valueOf(seconds, SI.SECOND);
    }
}
