package flexiblepower.manager.battery.sony;

import aQute.bnd.annotation.metatype.Meta;

@Meta.OCD
public interface SonyBatteryConfig {
    @Meta.AD(deflt = "SonyBatteryManager", description = "Unique resourceID")
    String resourceId();
    
    @Meta.AD(deflt = "4", description = "Number of 1.2 kWh IJ1001M Modules")
    int nrOfmodules();
    
    @Meta.AD(deflt="0.5", description = "initial State of Charge (0-1)")
    double initialSocRatio();
    
    @Meta.AD(deflt="20", description = "minimum desired fill level (percent)")
    double minimumFillLevelPercent();
    
    @Meta.AD(deflt="90", description = "maximum desired fill level (percent)")
    double maximumFillLevelPercent();
    
    @Meta.AD(deflt = "30", description = "The simulation time step for a recalculation of the state")
    long updateIntervalSeconds();
}
