package flexiblepower.manager.battery.powerwall;

import aQute.bnd.annotation.metatype.Meta;

@Meta.OCD
public interface PowerwallBatteryConfig {
    @Meta.AD(deflt = "PowerwallBatteryManager", description = "Unique resourceID")
    String resourceId();
    
    @Meta.AD(deflt="0.5", description = "initial State of Charge (0-1)")
    double initialSocRatio();
    
    @Meta.AD(deflt="20", description = "minimum desired fill level (percent)")
    double minimumFillLevelPercent();
    
    @Meta.AD(deflt="90", description = "maximum desired fill level (percent)")
    double maximumFillLevelPercent();
    
    @Meta.AD(deflt = "5", description = "The simulation time step for a recalculation of the state")
    long updateIntervalSeconds();
}
