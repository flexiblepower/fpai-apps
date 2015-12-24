package flexiblepower.manager.genericadvancedbattery;

import aQute.bnd.annotation.metatype.Meta;

@Meta.OCD
public interface GenericAdvancedBatteryConfig {
    @Meta.AD(deflt = "AdvancedBatteryManager", description = "Unique resourceID")
           String resourceId();

    @Meta.AD(deflt = "5", description = "Total Capacity in kWh")
           double totalCapacityKWh();

    @Meta.AD(deflt = "1500", description = "Maximum absolute charging rate in Watts")
           double maximumChargingRateWatts();

    @Meta.AD(deflt = "1500", description = "Maximum absolute discharging rate in Watts (Should be a positive value)")
           double maximumDischargingRateWatts();

    @Meta.AD(deflt = "24", description = "The rated capacity of the battery in Ah")
           double ratedCapacityAh();

    @Meta.AD(deflt = "6000", description = "Number of full discharge cycles until battery end of life (80% capacity)")
        int nrOfCyclesBeforeEndOfLife();

    // TODO: Make this less confusing, what does 0.5 mean when min and max are
    // 20 and 90...
    @Meta.AD(deflt = "0.5", description = "initial State of Charge (0-1).")
           double initialSocRatio();

    @Meta.AD(deflt = "20", description = "minimum desired fill level (percent)")
           double minimumFillLevelPercent();

    @Meta.AD(deflt = "90", description = "maximum desired fill level (percent)")
           double maximumFillLevelPercent();

    @Meta.AD(deflt = "9",
             description = "The number of modulesation steps between idle and charging and between idle and discharging")
        int nrOfModulationSteps();

    @Meta.AD(deflt = "5", description = "The simulation time step for a recalculation of the state")
        int updateIntervalSeconds();

    // TODO We could make the rated voltage, and the constants K, A and B
    // configurable, but this is a really advanced feature that requires Matlab
    // Simulink, so it is not in the first implementation.
    @Meta.AD(deflt = "52.6793", description = "*ADVANCED SETTINGS* Rated Voltage of the battery")
           double ratedVoltage();

    @Meta.AD(deflt = "0.011", description = "*ADVANCED SETTINGS* The constant K (unitless) of the battery batteryModel")
           double KValue();

    @Meta.AD(deflt = "24",
             description = "*ADVANCED SETTINGS* The constant Q in Ampere hours of the battery batteryModel")
           double QAmpereHours();

    @Meta.AD(deflt = "3",
             description = "*ADVANCED SETTINGS* Exponential Voltage constant used to calculate the Voltage in Volts")
           double constantA();

    @Meta.AD(deflt = "2.8",
             description = "*ADVANCED SETTINGS* Exponential Capacity constant used to calculate the Voltage.(Ah^-1)")
           double constantB();

    @Meta.AD(deflt = "0.036", description = "*ADVANCED SETTINGS* The internal resistance in Ohms")
           double internalResistanceOhms();
}
