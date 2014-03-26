package org.flexiblepower.miele.dishwasher.driver;

import static javax.measure.unit.NonSI.HOUR;
import static javax.measure.unit.NonSI.KWH;

import java.util.Date;
import java.util.Map;

import javax.measure.Measure;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.protocol.mielegateway.api.ActionPerformer;
import org.flexiblepower.protocol.mielegateway.api.ActionResult;
import org.flexiblepower.protocol.mielegateway.api.MieleResourceDriver;
import org.flexiblepower.rai.values.EnergyProfile;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherControlParameters;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;
import org.flexiblepower.time.TimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DishwasherDriver extends MieleResourceDriver<DishwasherState, DishwasherControlParameters> implements
                                                                                                       org.flexiblepower.ral.drivers.dishwasher.DishwasherDriver {
    private static final Logger log = LoggerFactory.getLogger(DishwasherDriver.State.class);

    static final class State implements DishwasherState {
        private final boolean isConnected;
        private final Date startTime;
        private final String program;

        State(Date startTime, String program) {
            isConnected = true;
            this.startTime = startTime;
            this.program = program;
        }

        State() {
            isConnected = false;
            startTime = null;
            program = "No program selected";
        }

        @Override
        public boolean isConnected() {
            return isConnected;
        }

        @Override
        public Date getStartTime() {
            return startTime;
        }

        @Override
        public String getProgram() {
            return program;
        }

        @Override
        public EnergyProfile getEnergyProfile() {
            return EnergyProfile.create().add(Measure.valueOf(1, HOUR), Measure.valueOf(1, KWH)).build();
        }

    }

    public DishwasherDriver(ActionPerformer actionPerformer, TimeService timeService) {
        super(actionPerformer, timeService);
    }

    @Override
    public void updateState(Map<String, String> information) {
        // TODO: There is much more information, what to do with it?
        String state = information.get("State");
        Date startTime = parseDate(information.get("Start Time"));
        Integer smartStart = parseTime(information.get("Smart Start"));
        String program = information.get("Program");
        String phase = information.get("Phase");
        Integer remainingTime = parseTime(information.get("Remaining Time"));
        Integer duration = parseTime(information.get("Duration"));

        State dishwasherState = new State(startTime, program);
        publish(new Observation<DishwasherState>(timeService.getTime(), dishwasherState));
    }

    @Override
    public void setControlParameters(DishwasherControlParameters resourceControlParameters) {
        if (resourceControlParameters.getStartProgram()) {
            ActionResult result = performAction("Start");
            if (!result.isOk()) {
                log.warn("Coul not start the dishwasher: {}", result.getMessage());
            }
        }
    }
}
