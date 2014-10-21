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
        GeneratorLevel generatorLevel = simulation.getGeneratorLevel();
        int intLevel = generatorLevel.getIntLevel();
        System.out.println("widget updating to level " + intLevel);
        return new Update(String.format("%2.1f W", intLevel));
    }

    @Override
    public String getTitle(Locale locale) {
        return "Uncontrolled Simulation";
    }
}
