package org.flexiblepower.miele.dishwasher.manager;

import java.io.IOException;
import java.util.Date;
import java.util.Locale;

import org.flexiblepower.ral.drivers.dishwasher.DishwasherControlParameters;
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

    private final MieleDishwasherManager dishwasher;

    public DishwasherWidget(MieleDishwasherManager dishwasher) {
        this.dishwasher = dishwasher;

    }

    public MieleDishwasherManager getDishwasher() {
        return dishwasher;
    }

    public Update update(Locale locale) {
        DishwasherState state = null;
        state = dishwasher.getCurrentState();
        if (state != null) {
            return new Update(state.getProgram(), state.getStartTime());
        } else {
            return new Update("No Program Selected", null);
        }
    }

    public Update startProgram(Locale locale) throws IOException {
        dishwasher.getDriver().setControlParameters(new DishwasherControlParameters() {
            @Override
            public boolean getStartProgram() {
                return true;
            }
        });
        return update(locale);
    }

    @Override
    public String getTitle(Locale locale) {
        return "Miele@Home Dishwasher";
    }
}
