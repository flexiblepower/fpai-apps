package org.flexiblepower.simulation.dishwasher.manager;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.flexiblepower.ral.drivers.dishwasher.DishwasherControlParameters;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;
import org.flexiblepower.ui.Widget;

public class DishwasherWidget implements Widget {

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

    private final TimeShifterSimulationManager dishwasher;

    public DishwasherWidget(TimeShifterSimulationManager dishwasher) {
        this.dishwasher = dishwasher;

    }

    public TimeShifterSimulationManager getDishwasher() {
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

    public void startSimulation() {
        dishwasher.startSimulation();
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
        return "Time Shifter Simulation";
    }
}
