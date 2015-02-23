package org.flexiblepower.simulation.api.dishwasher;

import java.util.Date;

import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;

public interface DishwasherSimulation {
    DishwasherState getLatestState();

    void setIdle();

    void setProgram(String program, Date startTime);
}
