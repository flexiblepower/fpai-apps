package org.flexiblepower.simulation.profile.timeshifter;

import java.util.NavigableMap;

public class TimeShifterProfile {

    private final int validFrom;
    private final int endBefore;
    private final NavigableMap<Float, Float> powerAtMinutesSinceStart;

    public TimeShifterProfile(int validFrom, int endBefore, NavigableMap<Float, Float> powerAtMinutesSinceStart) {
        this.validFrom = validFrom;
        this.endBefore = endBefore;
        this.powerAtMinutesSinceStart = powerAtMinutesSinceStart;
    }

    public int getValidFrom() {
        return validFrom;
    }

    public int getEndBefore() {
        return endBefore;
    }

    public NavigableMap<Float, Float> getPowerAtMinutesSinceStart() {
        return powerAtMinutesSinceStart;
    }

    @Override
    public String toString() {
        return "TimeShifterProfile [validFrom=" + validFrom
               + ", endBefore="
               + endBefore
               + ", powerAtMinutesSinceStart="
               + powerAtMinutesSinceStart
               + "]";
    }
}
