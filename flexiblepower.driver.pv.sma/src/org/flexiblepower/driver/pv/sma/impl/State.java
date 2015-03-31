package org.flexiblepower.driver.pv.sma.impl;

import java.util.Date;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.driver.pv.sma.SmaInverterState;

class State implements SmaInverterState {
    public static State createEmpty(boolean isSunUp) {
        return new State(Measure.valueOf(0, SI.WATT), null, false, isSunUp, null, null);
    }

    private final Measurable<Power> demand;
    private final Date time;
    private final boolean isConnected;
    private final boolean isSunUp;
    private final Measurable<Energy> lifetimeProduction;
    private final Measurable<Energy> todayProduction;

    public State(Measurable<Power> demand,
                 Date time,
                 boolean isConnected,
                 boolean isSunUp,
                 Measurable<Energy> lifetimeProduction,
                 Measurable<Energy> todayProduction) {
        this.demand = demand;
        this.time = time;
        this.isConnected = isConnected;
        this.isSunUp = isSunUp;
        this.lifetimeProduction = lifetimeProduction;
        this.todayProduction = todayProduction;
    }

    @Override
    public Measurable<Power> getCurrentUsage() {
        return demand;
    }

    @Override
    public Date getTime() {
        return time;
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public boolean isSunUp() {
        return isSunUp;
    }

    @Override
    public Measurable<Energy> getLifetimeProduction() {
        return lifetimeProduction;
    }

    @Override
    public Measurable<Energy> getTodayProduction() {
        return todayProduction;
    }

    @Override
    public String toString() {
        return "State [demand=" + demand
                + ", time="
                + time
                + ", isConnected="
                + isConnected
                + ", lifetimeProduction="
                + lifetimeProduction
                + ", todayProduction="
                + todayProduction
                + "]";
    }
}
