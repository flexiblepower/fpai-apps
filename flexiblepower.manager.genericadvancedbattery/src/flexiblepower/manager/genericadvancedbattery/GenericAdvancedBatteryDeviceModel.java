package flexiblepower.manager.genericadvancedbattery;

import java.util.Date;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.time.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericAdvancedBatteryDeviceModel implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(GenericAdvancedBatteryDeviceModel.class);

    // please refer to the simulink generic battery module help file for more
    // information
    private Measurable<Power> electricPower;

    /** Power entering of leaving the battery */
    private Measurable<Power> BattPower = Measure.valueOf(0, SI.WATT);
    /** Initialize the maximum power rating of the system */
    private final Measurable<Power> maxPower = Measure.valueOf(0, SI.WATT);
    /** scaling variable for the converter efficiency calculation */
    private double powerScale = 0;
    /** battery state of charge ratio (0-1) */
    private double soc;
    /**
     * initialise the variables for the soc of the battery at the start and end of a discharge cycle (0-1)
     */
    private double startSoC = 0;
    private double endSoC = 0;
    /**
     * initialise the variable to store the change in state of charge of the battery during a discharge cycle
     */
    private double dischargeDeltaSoc = 0;
    /** Efficiency used for charge and discharge in a ratio (0-1) */
    private double efficiency = 1;
    /** Constant internal battery voltage (V) */
    private final double E0;
    /** Internal Battery voltage variable (V) */
    private double Ebatt;
    /** Internal Battery voltage variable (V) */
    private double internalVolts;
    /** battery terminal voltage (V) */
    private double batteryVolts;
    /**
     * Polarization constant used for calculating the battery voltage (Ah^-1)
     */
    private final double K;
    /** instantaneous battery DC current variable (A) */
    private double i = 0;
    /** sum of capacity extracted from the battery (Ah) */
    private double it = 0;
    /** rated battery capacity Constant (Ah) */
    private final double ratedQ;
    /** rated battery capacity Constant (Ah) */
    private double Q;
    /** rated battery capacity for caculating the SOC (Ah) */
    private double Qsoc;
    /**
     * variable to keep track of the battery age as a ratio (0-1) of max rated capacity
     */
    private double Qratio = 1;
    /** exponential voltage constant used to calculate the voltage (V) */
    private final double A;
    /** exponential capacity constant used to calculate the voltage (Ah^-1) */
    private final double B;
    /** internal resistance of each battery module (ohms) */
    private double r;
    /** Variable to store the battery voltage form the last time step (V) */
    private double oldBatteryVolts = batteryVolts;
    /**
     * Initialize the battery terminal voltage error variable used by the voltage solver (V)
     */
    private double errorBV = 1;
    /** Change in state of charge variable for this time step ratio (0-1) */
    private double DeltaSoC = 0;
    /** Power from the last time step variable (Watts) */
    private Measurable<Power> oldPower = Measure.valueOf(0, SI.WATT);

    private GenericAdvancedBatteryMode mode = GenericAdvancedBatteryMode.IDLE;
    private GenericAdvancedBatteryMode oldMode = GenericAdvancedBatteryMode.IDLE;
    private final GenericAdvancedBatteryConfig configuration;
    private final FlexiblePowerContext context;
    private Date previousRun;

    /**
     * The charge power desired by the manager. Positive is charging, negative is discharging.
     */
    private double desiredChargePowerWatt;

    public GenericAdvancedBatteryDeviceModel(GenericAdvancedBatteryConfig configuration, FlexiblePowerContext context) {
        this.configuration = configuration;
        this.context = context;
        soc = configuration.initialSocRatio();

        // Set the constants of the batteryModel.
        E0 = configuration.ratedVoltage();
        Ebatt = E0;
        internalVolts = E0;
        batteryVolts = E0;
        K = configuration.KValue();
        A = configuration.constantA();
        B = configuration.constantB();

        Q = configuration.totalCapacityKWh() * 1000 / E0;
        ratedQ = Q;
        Qsoc = Q * (23.22 / 24);
        // initialise extracted capacity using the config soc
        it = (1 - soc) * Qsoc;
    }

    public GenericAdvancedBatteryMode getCurrentMode() {
        return mode;
    }

    @Override
    public synchronized void run() {
        // Check if the mode is allowed in the soc
        if ((soc < configuration.minimumFillLevelPercent() / 100 || batteryVolts < 32)
            && mode == GenericAdvancedBatteryMode.DISCHARGE) {
            mode = GenericAdvancedBatteryMode.IDLE;
        } else if (soc > configuration.maximumFillLevelPercent() / 100 && mode == GenericAdvancedBatteryMode.CHARGE) {
            mode = GenericAdvancedBatteryMode.IDLE;
        }

        // need to calculate the DoD for a full discharge cycle
        // Check for the start of a discharge cycle
        if (mode == GenericAdvancedBatteryMode.DISCHARGE && (oldMode != mode)) {
            // save the current soc
            startSoC = soc;
        }

        // Check for end of a discharge cycle
        if (oldMode == GenericAdvancedBatteryMode.DISCHARGE && (oldMode != mode)) {
            // save the current soc
            endSoC = soc;

            // calculate the change in soc for this discharge cycle (0-1)
            dischargeDeltaSoc = startSoC - endSoC;

            // calculate the change in capacity due to battery aging
            Qratio = getBatteryChangeCapacity(dischargeDeltaSoc) / 100;
            Q = Q - Q * Qratio;
            Qsoc = Qsoc - Qsoc * Qratio;

        }

        Date now = context.currentTime();
        Measurable<Duration> duration;
        if (previousRun == null) {
            duration = Measure.zero(SI.SECOND);
        } else {
            duration = TimeUtil.difference(previousRun, now);
        }

        // Calculate the Voltage and Current of the battery for the current mode
        if (mode == GenericAdvancedBatteryMode.CHARGE) {
            electricPower = Measure.valueOf(-1 * desiredChargePowerWatt, SI.WATT);

            // calculate the power going into the battery by using the converter
            // charge efficiency curve
            BattPower = Measure.valueOf((getChargeEfficiency(electricPower) / 100) * electricPower.doubleValue(SI.WATT),
                                        SI.WATT);

            // Check if the Power set point has changed
            if (electricPower.doubleValue(SI.WATT) != oldPower.doubleValue(SI.WATT)) {
                internalVolts = solveChargeBatteryEbatt();
            } else {
                // Calculate the voltage
                internalVolts = chargeBatteryEbatt(i);
            }

            // calculate the battery terminal voltage resulting from the
            // internal
            // resistance
            batteryVolts = internalVolts - (i * r);

        } else if (mode == GenericAdvancedBatteryMode.DISCHARGE) {

            electricPower = Measure.valueOf(-1 * desiredChargePowerWatt, SI.WATT);

            // calculate the power coming out of the battery by using the
            // converter discharge efficiency curve
            BattPower = Measure.valueOf(
                                        electricPower.doubleValue(SI.WATT)
                                        / (getDischargeEfficiency(electricPower) / 100), SI.WATT);

            // Check if the Power set point has changed has changed
            if (electricPower.doubleValue(SI.WATT) != oldPower.doubleValue(SI.WATT)) {
                internalVolts = solveDischargeBatteryEbatt();
            } else {
                // calculate the battery internal voltage during discharge
                internalVolts = dischargeBatteryEbatt(i);
            }

            // calculate the battery terminal voltage resulting from the
            // internal
            // resistance
            batteryVolts = internalVolts - (i * r);

        } else { // mode == AdvancedBatteryMode.IDLE
            electricPower = Measure.zero(SI.WATT);
            BattPower = Measure.zero(SI.WATT);

            // During idle that current i is 0. this makes the charge and
            // discharge voltage formulas identical
            // use the discharge formula
            // set i = 0;
            i = 0;
            batteryVolts = dischargeBatteryEbatt(i);
            // there is no current hence the internal voltage and the terminal
            // votlage are the same
        }

        // calculate the current (A)
        // convention is discharging is positive current
        i = BattPower.doubleValue(SI.WATT) / batteryVolts;

        // update 'it' charge added or removed
        it += i * duration.doubleValue(NonSI.HOUR);

        // change in SoC (0-1)
        DeltaSoC = (i * duration.doubleValue(NonSI.HOUR)) / Qsoc;
        // update the state of charge
        soc -= DeltaSoC;

        // save the current power set point to oldPower
        oldPower = electricPower;
        oldBatteryVolts = batteryVolts;
        oldMode = mode;

        previousRun = now;
        logger.info(
                    "Executed battery batteryModel at " + now
                    + ", mode is "
                    + mode
                    + ", fill level is "
                    + getCurrentFillLevel());
    }

    /** calculate the voltage of the system during discharge */
    private double dischargeBatteryEbatt(double i) {

        // calculate the internal battery voltage Ebatt

        Ebatt = E0 - K * (Q / (Q - it)) * i - K * (Q / (Q - it)) * it + A * Math.exp(-B * it);

        return Ebatt;
    }

    /** calculate the voltage of the system during charging */
    private double chargeBatteryEbatt(double i) {
        // calculate the internal battery voltage Ebatt

        Ebatt = E0 - K * (Q / (it + 0.1 * Q)) * i - K * (Q / (Q - it)) * it + A * Math.exp(-B * it);

        return Ebatt;
    }

    // Solve the battery voltage for a change in discharge power setpoint
    private double solveDischargeBatteryEbatt() {
        // estimate the current using the terminal voltage from the last time
        // step
        double solverI = BattPower.doubleValue(SI.WATT) / oldBatteryVolts;

        // loop until the error is less than 0.01 volts
        while (errorBV >= 0.0001) {
            Ebatt = E0 - K * (Q / (Q - it)) * solverI - K * (Q / (Q - it)) * it + A * Math.exp(-B * it);
            // calculate the error
            errorBV = Math.abs(batteryVolts - (Ebatt - (solverI * r)));
            batteryVolts = Ebatt - (solverI * r);
            solverI = BattPower.doubleValue(SI.WATT) / batteryVolts;
        }

        return Ebatt;
    }

    private double solveChargeBatteryEbatt() {
        // estimate the current using the terminal voltage from the last time
        // step
        double solverI = BattPower.doubleValue(SI.WATT) / oldBatteryVolts;

        // loop until the error is less than 0.01 volts
        while (errorBV >= 0.0001) {
            Ebatt = E0 - K * (Q / (it + 0.1 * Q)) * solverI - K * (Q / (Q - it)) * it + A * Math.exp(-B * it);
            // calculate the error
            errorBV = Math.abs(batteryVolts - (Ebatt - (solverI * r)));
            batteryVolts = Ebatt - (solverI * r);
            solverI = BattPower.doubleValue(SI.WATT) / batteryVolts;
        }

        return Ebatt;
    }

    public Measurable<Energy> getTotalCapacity() {
        return Measure.valueOf(configuration.totalCapacityKWh(), NonSI.KWH);
    }

    /**
     * Returns the efficiency (ranging from 0 - 100) given the current chargeP speed.
     *
     * @param chargeSpeed
     * @return efficiency from as a number from 0 - 100
     */
    public double getChargeEfficiency(Measurable<Power> chargeSpeed) {
        // efficiency curves are based on a 2000W converter
        // scale the power set point based on the max power rating of the unit
        powerScale = ((Math.abs((chargeSpeed.doubleValue(SI.KILO(SI.WATT))))
                       / (getMaximumChargeSpeed().doubleValue(SI.KILO(SI.WATT))))
                      * 2);
        if (powerScale < 0.009) {
            efficiency = 0;
        } else {
            efficiency = (0.9191 * (Math.pow(powerScale, 4)) - 4.17 * (Math.pow(powerScale, 3))
                          + 5.398 * (Math.pow(powerScale, 2))
                          + 95.06 * (powerScale)
                          - 0.8774)
                         / (powerScale - 0.007935);
        }
        return efficiency;
    }

    /**
     * Returns the efficiency (ranging from 0 - 100) given the current discharge speed.
     *
     * @param dischargeSpeed
     * @return efficiency from as a number from 0 - 100
     */
    public double getDischargeEfficiency(Measurable<Power> chargeSpeed) {
        // efficiency curves are based on a 2kW converter
        // scale the power set point based on the max power rating of the unit
        powerScale = ((Math.abs((chargeSpeed.doubleValue(SI.KILO(SI.WATT))))
                       / (getMaximumDischargeSpeed().doubleValue(SI.KILO(SI.WATT))))
                      * 2);

        efficiency = (-1.043 * (Math.pow(powerScale, 3)) + 1.836 * (Math.pow(powerScale, 2))
                      + 95.63 * powerScale
                      + 0.0002741)
                     / (powerScale + 0.01581);

        return efficiency;

    }

    public Measurable<Power> getMaximumChargeSpeed() {
        return Measure.valueOf(configuration.maximumChargingRateWatts(), SI.WATT);
    }

    public Measurable<Power> getMaximumDischargeSpeed() {
        return Measure.valueOf(configuration.maximumDischargingRateWatts(), SI.WATT);
    }

    /**
     * @return The percentage of charge in the battery. (0-100)
     */
    public Measurable<Dimensionless> getCurrentFillLevel() {
        return Measure.valueOf(soc * 100d, NonSI.PERCENT);
    }

    /**
     * Set the desired charge Power. Positive means charging, negative means discharging.
     *
     * This method also updates the mode.
     *
     * @param chargePower
     *            Desired charge power (or discharge power if negative)
     */
    public void setDesiredChargePower(Measurable<Power> chargePower) {
        double newChargePowerWatt = chargePower.doubleValue(SI.WATT);
        if (newChargePowerWatt != desiredChargePowerWatt) {
            desiredChargePowerWatt = newChargePowerWatt;
            desiredChargePowerWatt = chargePower.doubleValue(SI.WATT);
            if (desiredChargePowerWatt > 0) {
                mode = GenericAdvancedBatteryMode.CHARGE;
            } else if (desiredChargePowerWatt < 0) {
                mode = GenericAdvancedBatteryMode.DISCHARGE;
            } else {
                mode = GenericAdvancedBatteryMode.IDLE;
            }
            run();
        }
    }

    public double getCurrentInAmps() {
        return -i;
    }

    public double getBatteryVolts() {
        return batteryVolts;
    }

    public double getBatteryAge() {
        return Q / ratedQ * 100;
    }

    public double getDesiredChargePowerWatt() {
        return desiredChargePowerWatt;
    }

    public Measurable<Power> getElectricPower() {
        return Measure.valueOf(-electricPower.doubleValue(SI.WATT), SI.WATT);
    }

    /**
     * Calculate the % reduction of total capacity due to battery aging battery end of life is when the capacity is
     * reduced to 80%
     */
    public double getBatteryChangeCapacity(double dischargeDeltaSoc) {

        double CyclePercent = 0.0053; // % drop in rated capacity / cycle

        double changeCapacityPercentage = (dischargeDeltaSoc * CyclePercent / 100) * 100;

        return changeCapacityPercentage;
    }

}
