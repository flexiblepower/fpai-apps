package org.flexiblepower.simulation.lighting;

import java.util.Date;

import javax.measure.Measurable;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.ral.drivers.uncontrolled.PowerState;

public class LightingUpdate implements PowerState {

    private final Date currentTime, timeSunUp, timeSunDown;
    private final Measurable<Power> power;
    private final double demand;
    private final int numberOfLights;

    public Date getCurrentTime() {
        return currentTime;
    }

    public Date getTimeSunUp() {
        return timeSunUp;
    }

    public Date getTimeSunDown() {
        return timeSunDown;
    }

    public LightingUpdate(Date currentTime,
                          Date timeSunUp,
                          Date timeSunDown,
                          Measurable<Power> power,
                          int numberOfLights) {
        super();
        this.currentTime = currentTime;
        this.timeSunUp = timeSunUp;
        this.timeSunDown = timeSunDown;
        this.power = power;
        this.numberOfLights = numberOfLights;
        demand = power.doubleValue(SI.WATT);
    }

    @Override
    public boolean isConnected() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Measurable<Power> getCurrentUsage() {
        return power;
    }

}
