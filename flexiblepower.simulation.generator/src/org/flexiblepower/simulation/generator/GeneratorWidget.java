package org.flexiblepower.simulation.generator;

import java.util.Locale;

import org.flexiblepower.ui.Widget;

public class GeneratorWidget implements Widget {
    public static class Update {
        private final String level;

        public Update(String level) {
            this.level = level;
        }

        public String getLevel() {
            return level;
        }
    }

    private final GeneratorSimulation simulation;

    public GeneratorWidget(GeneratorSimulation simulation) {
        this.simulation = simulation;
    }

    public Update update() {
        String level = Integer.toString(simulation.getGeneratorLevel().getIntLevel());
        return new Update(level);
    }

    @Override
    public String getTitle(Locale locale) {
        return "Generator Simulation";
    }
}
