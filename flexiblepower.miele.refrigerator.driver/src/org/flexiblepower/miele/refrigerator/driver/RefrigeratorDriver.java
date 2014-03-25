package org.flexiblepower.miele.refrigerator.driver;

import java.util.Map;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Temperature;
import javax.measure.unit.SI;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.protocol.mielegateway.api.MieleResourceDriver;
import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorControlParameters;
import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorState;
import org.flexiblepower.time.TimeService;

import aQute.bnd.annotation.component.Reference;

public class RefrigeratorDriver extends MieleResourceDriver<RefrigeratorState, RefrigeratorControlParameters> implements
                                                                                                             org.flexiblepower.ral.drivers.refrigerator.RefrigeratorDriver {

    static final class State implements RefrigeratorState {
        private final boolean isConnected;
        private final Measurable<Temperature> currTemp, targetTemp, minTemp;
        private final boolean supercool;

        State() {
            isConnected = false;
            currTemp = targetTemp = minTemp = null;
            supercool = false;
        }

        State(Measurable<Temperature> temp,
              Measurable<Temperature> targetTemp,
              Measurable<Temperature> minTemp,
              boolean inSuperCool) {
            isConnected = true;
            currTemp = temp;
            this.targetTemp = targetTemp;
            this.minTemp = minTemp;
            supercool = inSuperCool;
        }

        @Override
        public boolean isConnected() {
            return isConnected;
        }

        @Override
        public Measurable<Temperature> getCurrentTemperature() {
            return currTemp;
        }

        @Override
        public Measurable<Temperature> getTargetTemperature() {
            return targetTemp;
        }

        @Override
        public Measurable<Temperature> getMinimumTemperature() {
            return minTemp;
        }

        @Override
        public boolean getSupercoolMode() {
            return supercool;
        }
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    @Override
    public void setControlParameters(RefrigeratorControlParameters resourceControlParameters) {
        if (resourceControlParameters.getSupercoolMode() ^ getLastObservation().getValue().getSupercoolMode()) {
            if (resourceControlParameters.getSupercoolMode()) {
                performAction("SuperCooling On");
            } else {
                performAction("SuperCooling Off");
            }
        }
    }

    @Override
    public void updateState(Map<String, String> information) {
        Measurable<Temperature> currentTemp = parseTemperature(information.get("Current Temperature"));
        Measurable<Temperature> targetTemp = parseTemperature(information.get("Target Temperature"));
        Measurable<Temperature> minTemp = Measure.valueOf(4, SI.CELSIUS);
        String state = information.get("State");
        State currentState = new State(currentTemp, targetTemp, minTemp, "Super Cooling".equals(state));

        publish(new Observation<RefrigeratorState>(timeService.getTime(), currentState));
    }
}
