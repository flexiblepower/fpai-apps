package org.flexiblepower.simulation.dishwasher;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.measure.Measure;
import javax.measure.unit.NonSI;

import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;
import org.flexiblepower.ui.Widget;

public class DishwasherWidget implements Widget {
    private static final DateFormat FORMATTER = new SimpleDateFormat("HH:mm:ss");

    public static class Update {
        private final String program;
        private final String startedAt;
        private final String latestStartTime;

        public Update(String program, Date startedAt, Date latestStartTime) {
            this.program = program;
            this.startedAt = startedAt == null ? "Not yet started" : FORMATTER.format(startedAt);
            this.latestStartTime = latestStartTime == null ? "Not yet planned" : FORMATTER.format(latestStartTime);
        }

        public String getProgram() {
            return program;
        }

        public String getStartedAt() {
            return startedAt;
        }

        public String getLatestStartTime() {
            return latestStartTime;
        }
    }

    private final DishwasherSimulationImpl dishwasher;

    public DishwasherWidget(DishwasherSimulationImpl dishwasher) {
        this.dishwasher = dishwasher;
    }

    public DishwasherSimulationImpl getDishwasher() {
        return dishwasher;
    }

    private DishwasherState state;

    public Update update(Locale locale) {
        state = dishwasher.getLatestState();
        return new Update(state.getProgram(), state.getStartTime(), state.getLatestStartTime());
    }

    public Update startProgram(Locale locale) throws IOException {
        dishwasher.setProgram("Simulated Program", Measure.valueOf(10, NonSI.MINUTE));
        return update(locale);
    }

    @Override
    public String getTitle(Locale locale) {
        return "Dishwasher driver simulation";
    }
}
