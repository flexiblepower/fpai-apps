package flexiblepower.manager.advancedbattery;

import aQute.bnd.annotation.metatype.Meta;

@Meta.OCD
public interface AdvancedBatteryConfig {
    @Meta.AD(deflt = "AdvancedBatteryManager", description = "Unique resourceID")
    String resourceId();
    
    @Meta.AD(deflt = "4", description = "Number of 1.2 kWh IJ1001M Modules")
    int modules();
    
    @Meta.AD(deflt="0.5", description = "initial State of Charge (0-1)")
    double initialSocRatio();
    
    @Meta.AD(deflt="0.2", description = "minimum desired State of Charge (0-1)")
    double minimumSocRatio();
    
    @Meta.AD(deflt="0.9", description = "maximum desired State of Charge (0-1)")
    double maximumSocRatio();
    
    @Meta.AD(deflt = "5", description = "The simulation time step for a recalculation of the state.")
    long updateIntervalSeconds();
}
