package org.flexiblepower.simulation.lighting;

import java.util.Locale;

import org.flexiblepower.ui.Widget;

public class LightingWidget implements Widget {
    private final LightingSimulation simulation;

    public LightingWidget(LightingSimulation simulation) {
        this.simulation = simulation;
    }

    @Override
    public String getTitle(Locale locale) {
        if ("nl".equals(locale.getLanguage())) {
            return "Public Lighting Simulation";
        } else {
            return "Public Lighting Simulation";
        }
    }

    public LightingUpdate update() {
        return simulation.getNextUpdate();
    }
}
