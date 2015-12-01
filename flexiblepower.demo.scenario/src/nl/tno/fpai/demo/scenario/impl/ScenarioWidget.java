package nl.tno.fpai.demo.scenario.impl;

import java.util.Locale;
import java.util.Set;

import nl.tno.fpai.demo.scenario.ScenarioManager;

import org.flexiblepower.ui.Widget;

import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;

@Component(properties = "widget.ranking=1")
// was 999999
public class ScenarioWidget implements Widget {

    private ScenarioManagerImpl scenarioManager;

    @Reference
    public void setScenarioManager(ScenarioManager scenarioManager) {
        this.scenarioManager = (ScenarioManagerImpl) scenarioManager;
    }

    @Override
    public String getTitle(Locale locale) {
        return "R.E.X. Heinsberg";
    }

    public Set<String> getNames() {
        return scenarioManager.getScenarios();
    }

    public void startScenario(String name) {
        scenarioManager.startScenario(name);
    }

    public String getStatus() {
        return scenarioManager.getStatus();
    }
}
