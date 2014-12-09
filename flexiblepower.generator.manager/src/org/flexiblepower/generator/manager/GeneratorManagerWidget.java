package org.flexiblepower.generator.manager;

import java.util.Locale;

import org.flexiblepower.ui.Widget;

public class GeneratorManagerWidget implements Widget {

    public static class Update {
        private final String level;

        public Update(String level) {
            this.level = level;
        }

        public String getLevel() {
            return level;
        }
    }

    private final GeneratorManager manager;

    public GeneratorManagerWidget(GeneratorManager manager) {
        this.manager = manager;
    }

    public Update update() {
        String level = Integer.toString(manager.getMostRecentState().getGeneratorLevel().getIntLevel());
        return new Update(level);
    }

    public Update changeLevel() {
        int oldLevel = manager.getMostRecentState().getGeneratorLevel().getIntLevel();
        int newLevel = oldLevel;

        Integer element = manager.getPowerValues().lower(oldLevel);
        if (element == null) {
            newLevel = manager.getPowerValues().last();
        }
        else {
            newLevel = element;
        }

        manager.getMostRecentState().getGeneratorLevel().setLevel(newLevel);
        return update();
    }

    @Override
    public String getTitle(Locale locale) {
        return "Generator Simulation";
    }

}
