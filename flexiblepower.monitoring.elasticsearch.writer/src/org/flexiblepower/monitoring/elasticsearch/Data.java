package org.flexiblepower.monitoring.elasticsearch;

import org.flexiblepower.observation.Observation;

public class Data {
    private final String type;
    private final Observation<?> observation;

    public Data(String type, Observation<?> observation) {
        this.type = type;
        this.observation = observation;
    }

    public String getType() {
        return type;
    }

    public Observation<?> getObservation() {
        return observation;
    }
}
