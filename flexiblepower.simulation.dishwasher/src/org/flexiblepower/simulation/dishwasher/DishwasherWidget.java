package org.flexiblepower.simulation.dishwasher;

import java.io.IOException;
import java.util.Date;
import java.util.Locale;

import javax.measure.Measure;
import javax.measure.unit.NonSI;

import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;
import org.flexiblepower.ui.Widget;

public class DishwasherWidget implements Widget {
    public static class Update {
        private final String program;
        private final String date;

        public Update(String program, Date date) {
            this.program = program;
            if (date != null) {
                this.date = date.toString();
            } else {
                this.date = "None";
            }
        }

        public String getProgram() {
            return program;
        }

        public String getDate() {
            return date;
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
        if (state != null) {
            return new Update(state.getProgram(), state.getStartTime());
        } else {
            return new Update("No Program Selected", null);
        }
    }

    public Update startProgram(Locale locale) throws IOException {
        dishwasher.setProgram("Simulated Program", Measure.valueOf(2, NonSI.HOUR));
        return update(locale);
    }

    @Override
    public String getTitle(Locale locale) {
        return "Dishwasher driver simulation";
    }
}
