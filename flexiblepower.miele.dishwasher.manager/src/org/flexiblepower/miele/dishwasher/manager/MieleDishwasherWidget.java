package org.flexiblepower.miele.dishwasher.manager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;
import org.flexiblepower.ui.Widget;

public class MieleDishwasherWidget implements Widget {

    public static class Update {
        private final String program;
        private final String date;
        private final String mode;

        public Update(String program, Date date, boolean running) {
            this.program = program;
            if (date != null) {
                SimpleDateFormat ft = new SimpleDateFormat("HH:mm:ss dd-MM-yyyy");
                this.date = ft.format(date);
            } else {
                this.date = "None";
            }

            if (running) {
                mode = "Running";
            } else {
                mode = "Idle";
            }
        }

        public String getProgram() {
            return program;
        }

        public String getDate() {
            return date;
        }

        public String getMode() {
            return mode;
        }
    }

    private final MieleDishwasherManager dishwasher;

    public MieleDishwasherWidget(MieleDishwasherManager dishwasher) {
        this.dishwasher = dishwasher;
    }

    public MieleDishwasherManager getDishwasher() {
        return dishwasher;
    }

    public Update update(Locale locale) {
        DishwasherState state = null;
        state = dishwasher.getCurrentState();
        if (state != null) {
            return new Update(state.getProgram(), state.getStartTime(), state.isConnected());
        } else {
            return new Update("No Program Selected", null, false);
        }
    }

    @Override
    public String getTitle(Locale locale) {
        return "Dishwasher manager";
    }
}
