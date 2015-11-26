package org.flexiblepower.ledstrip.manager;

import java.util.Locale;

import org.flexiblepower.ui.Widget;

public class LedstripManagerWidget implements Widget {

    public static class Update {
        private final String level;

        public Update(String level) {
            this.level = level;
        }

        public String getLevel() {
            return level;
        }
    }

    private final LedstripManager manager;

    public LedstripManagerWidget(LedstripManager manager) {
        this.manager = manager;
    }

    public Update update() {
        if (manager.getMostRecentState() == null) {
            return new Update("0");
        }

        String level = Integer.toString(manager.getMostRecentState().getLedstripLevel().getIntLevel());
        return new Update(level);
    }

    public Update changeLevel() {
        if (manager.getMostRecentState() == null) {
            return null;
        }
        int oldLevel = manager.getMostRecentState().getLedstripLevel().getIntLevel();
        int newLevel = oldLevel;

        Integer element = manager.getPowerValues().lower(oldLevel);
        if (element == null) {
            newLevel = manager.getPowerValues().last();
        } else {
            newLevel = element;
        }

        manager.getMostRecentState().getLedstripLevel().setLevel(newLevel);
        return update();
    }

    @Override
    public String getTitle(Locale locale) {
        return "Ledstrip Panel";
    }
}
