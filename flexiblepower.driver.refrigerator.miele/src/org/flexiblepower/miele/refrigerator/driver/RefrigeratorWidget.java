package org.flexiblepower.miele.refrigerator.driver;

import java.util.Date;
import java.util.Locale;

import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorState;
import org.flexiblepower.ui.Widget;

public class RefrigeratorWidget implements Widget {

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

    private final RefrigeratorDriver refrigerator;

    public RefrigeratorWidget(RefrigeratorDriver refrigerator) {
        this.refrigerator = refrigerator;

    }

    public RefrigeratorDriver getDishwasher() {
        return refrigerator;
    }

    private RefrigeratorState state;

    // public Update update(Locale locale) {
    // state = refrigerator.getCurrentState();
    // if (state != null) {
    // return new Update(state.getProgram(), state.getStartTime());
    // } else {
    // return new Update("No Program Selected", null);
    // }
    // }

    // public Update startProgram(Locale locale) throws IOException {
    // refrigerator.startNow();
    // return update(locale);
    // }

    @Override
    public String getTitle(Locale locale) {
        return "Miele@Home Refrigerator";
    }
}
