package flexiblepower.manager.advancedbattery;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.flexiblepower.ral.drivers.battery.BatteryState;

public class AdvancedBatteryDeviceModel implements Runnable {

	
	// please refer to the simulink generic battery module help file for more information
    private Measurable<Power> electricPower;
    private Measurable<Power> BattPower = Measure.valueOf(0, SI.WATT);// power entering of leaving the battery
    private Measurable<Power> maxPower = Measure.valueOf(0, SI.WATT);// initialise the maximum power rating of the
                                                                     // system
    private double powerScale = 0;// scaling variable for the converter efficiency calculation
    private double soc;// battery state of charge
    private final double modules = 0; // number of sony fortellion 1.2kWh modules
    private double efficiency = 1;// efficiency of the converter charge/discharge cycle
    private final double E0 = 16 * 3.2925; // constant voltage (V)
    private double Ebatt = E0;// non linear voltage (current battery internal voltage) (V)
    private double oldEbatt = Ebatt; // initialise the internal battery voltage from the last time step
    private final double K = 0.011517; // Polarization constant (Ah^-1)
    private double i = 0; // Battery current (A)
    private double it = 0; // extracted capacity (Ah)
    private double Q = 8 * 3;// maximum battery capacity (Ah)
    private final double A = 2.0615; // exponential voltage (V)
    private final double B = 3.75; // exponential capacity (Ah)^-1
    private final double r = 0.036; // internal resistance of module (ohms)
    private double batteryVolts = E0;// Battery terminal voltage
    private double oldBatteryVolts = batteryVolts;// initialise the Battery terminal voltage from the last time
    private double errorBV = 1; // initialise the battery terminal voltage error used by the solver
    private double DeltaSoC = 0; // initialise the change of state of charge variable
    // step
    private Measurable<Power> oldPower = Measure.valueOf(0, SI.WATT);// initialise the power from the last time step to
                                                                     // 0
    

	AdvancedBatteryMode mode;
	private AdvancedBatteryConfig configuration;
	
	public AdvancedBatteryDeviceModel(AdvancedBatteryConfig configuration) {
		this.configuration = configuration;
	}

	public AdvancedBatteryMode getCurrentMode() {
		return mode;
	}

	@Override
	public void run() {
		mode = getCurrentMode();
        
        // Calculate the Voltage and Current of the battery for the current mode
        if (mode == getCurrentMode().CHARGE) {
        	electricPower = batteryState.getChargeSpeed(); // get the power set point from the controller

            // calculate the power going into the battery by using the converter charge efficiency curve
            BattPower = Measure.valueOf((getChargeEfficiency(<XX>) / 100) * electricPower.doubleValue(SI.WATT), SI.WATT);

            // Check if the Power set point has changed has changed
            if (electricPower.doubleValue(SI.WATT) != oldPower.doubleValue(SI.WATT)) {
                solveChargeBatteryVolts();
            }
            else {
                // Calculate the voltage
                chargeBatteryVolts();
            }

            // calculate the current (A)
            // convention is charging is negative current
            i = BattPower.doubleValue(SI.WATT) / batteryVolts;

        } else if (mode == getCurrentMode().DISCHARGE) {

            // calculate the power coming out of the battery by using the converter discharge efficiency curve
            BattPower = Measure.valueOf(electricPower.doubleValue(SI.WATT) / (disChargeEff() / 100), SI.WATT);

            // Check if the Power set point has changed has changed
            if (electricPower.doubleValue(SI.WATT) != oldPower.doubleValue(SI.WATT)) {
                solveDischargeBatteryVolts();
            }
            else {
                // calculate the battery terminal voltage during discharge
                dischargeBatteryVolts();
            }

            // calculate the current (A)
            // convention is discharging is positive current
            i = BattPower.doubleValue(SI.WATT) / batteryVolts;

        } else {
            electricPower = Measure.zero(SI.WATT);
        }

        // update 'it' charge added or removed
        it += i * duration.doubleValue(NonSI.HOUR);

        // change in SoC (0-1)
        DeltaSoC = (i * duration.doubleValue(NonSI.HOUR)) / Q;
        // update the state of charge
        soc -= DeltaSoC;

        // save the current power set point to oldPower
        oldPower = electricPower;
        oldEbatt = Ebatt;
        oldBatteryVolts = batteryVolts;
		
		
		// TODO Auto-generated method stub
		
	}
	
	// calculate the voltage of the system during discharge
    public double dischargeBatteryVolts() {

        // calculate the internal battery voltage Ebatt

        Ebatt = E0 - K * (Q / (Q - it)) * i - K * (Q / (Q - it)) * it + A * Math.exp(-B * it);

        // calculate the battery terminal voltage resulting from the internal resistance
        batteryVolts = Ebatt - (i * r);
        return batteryVolts;
    }

    // calculate the voltage of the system during charging
    public double chargeBatteryVolts() {
        // calculate the internal battery voltage Ebatt

        Ebatt = E0 - K * (Q / (it + 0.1 * Q)) * i - K * (Q / (Q - it)) * it + A * Math.exp(-B * it);

        // calculate the battery terminal voltage resulting from the internal resistance
        batteryVolts = Ebatt - (i * r);
        return batteryVolts;
    }
	
	 // Solve the battery voltage for a change in discharge power setpoint
    public double solveDischargeBatteryVolts() {
        // estimate the current using the terminal voltage from the last time step
        i = BattPower.doubleValue(SI.WATT) / oldBatteryVolts;

        // loop until the error is less than 0.01 volts
        while (errorBV >= 0.0001) {
            Ebatt = E0 - K * (Q / (Q - it)) * i - K * (Q / (Q - it)) * it + A * Math.exp(-B * it);
            errorBV = Math.abs(batteryVolts - (Ebatt - (i * r)));// calculate the error
            batteryVolts = Ebatt - (i * r);
            i = BattPower.doubleValue(SI.WATT) / batteryVolts;
        }

        batteryVolts = Ebatt - (i * r);

        return batteryVolts;
    }
	
	 public double solveChargeBatteryVolts() {

	        // estimate the current using the terminal voltage from the last time step
	        i = BattPower.doubleValue(SI.WATT) / oldBatteryVolts;

	        // loop until the error is less than 0.01 volts
	        while (errorBV >= 0.0001) {
	            Ebatt = E0 - K * (Q / (it + 0.1 * Q)) * i - K * (Q / (Q - it)) * it + A * Math.exp(-B * it);
	            errorBV = Math.abs(batteryVolts - (Ebatt - (i * r)));// calculate the error
	            batteryVolts = Ebatt - (i * r);
	            i = BattPower.doubleValue(SI.WATT) / batteryVolts;
	        }

	        batteryVolts = Ebatt - (i * r);

	        return batteryVolts;
	    }	

	public Measurable<Energy> getTotalCapacity() {
		return Measure.valueOf(configuration.nrOfmodules() * 1.2, NonSI.KWH);
	}

	public double getChargeEfficiency(Measurable<Power> chargeSpeed) {
		// efficiency curves are based on a 2000W converter
        // scale the power set point based on the max power rating of the unit
        powerScale = ((Math.abs((chargeSpeed.doubleValue(SI.KILO(SI.WATT)))) / (getMaximumChargeSpeed().doubleValue(SI.KILO(SI.WATT)))) * 2);

        
        if (powerScale < 0.009) {
            efficiency = 0;
        }
        else {
            efficiency = (0.9191 * (Math.pow(powerScale, 4))
                          - 4.17
                          * (Math.pow(powerScale, 3))
                          + 5.398
                          * (Math.pow(powerScale, 2))
                          + 95.06
                          * (powerScale) - 0.8774) / (powerScale - 0.007935);
        }
        return efficiency;
	}

	public double getDischargeEfficiency(Measurable<Power> chargeSpeed) {
		// efficiency curves are based on a 2kW converter
        // scale the power set point based on the max power rating of the unit
		powerScale = ((Math.abs((chargeSpeed.doubleValue(SI.KILO(SI.WATT)))) / (getMaximumDischargeSpeed().doubleValue(SI.KILO(SI.WATT)))) * 2);

		efficiency = (-1.043 * (Math.pow(powerScale, 3)) + 1.836 * (Math.pow(powerScale, 2)) + 95.63 * powerScale + 0.0002741) / (powerScale + 0.01581);

        return efficiency;

	}
	
	public Measurable<Power> getMaximumChargeSpeed() {
		// TODO actually use
		return Measure.valueOf(configuration.nrOfmodules() <= 1 ? 2500 : 5000, SI.WATT);
	}

	public Measurable<Power> getMaximumDischargeSpeed() {
		// TODO actually use
		return Measure.valueOf(configuration.nrOfmodules() <= 1 ? -2500 : -5000, SI.WATT);
	}

	public Measurable<Dimensionless> getCurrentFillLevel() {
		// TODO Auto-generated method stub
		return null;
	}
	
}
