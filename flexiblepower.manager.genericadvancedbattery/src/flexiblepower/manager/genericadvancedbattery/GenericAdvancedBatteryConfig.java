package flexiblepower.manager.genericadvancedbattery;

import aQute.bnd.annotation.metatype.Meta;

@Meta.OCD
public interface GenericAdvancedBatteryConfig {
    @Meta.AD(deflt = "AdvancedBatteryManager", description = "Unique resourceID")
    String resourceId();
    
    @Meta.AD(deflt = "5", description = "Total Capacity in kWh")
    double totalCapacityKWh();

    @Meta.AD(deflt = "1500", description = "Maximum charging rate in Watts")
    double maximumChargingRateWatts();
    
    @Meta.AD(deflt = "1500", description = "Maximum discharging rate in Watts")
    double maximumDischargingRateWatts();
    
    @Meta.AD(deflt = "6000", description = "Number of full discharge cycles until battery end of life (80% capacity)")
    int numberOfCyclesBeforeEndOfLife();
    
    //TODO: Make this less confusing, what does 0.5 mean when min and max are 20 and 90...
    @Meta.AD(deflt="0.5", description = "initial State of Charge (0-1).")
    double initialSocRatio();
    
    @Meta.AD(deflt="20", description = "minimum desired fill level (percent)")
    double minimumFillLevelPercent();
    
    @Meta.AD(deflt="90", description = "maximum desired fill level (percent)")
    double maximumFillLevelPercent();
    
    @Meta.AD(deflt = "30", description = "The simulation time step for a recalculation of the state")
    long updateIntervalSeconds();
}
