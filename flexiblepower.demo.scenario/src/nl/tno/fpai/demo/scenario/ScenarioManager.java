package nl.tno.fpai.demo.scenario;

import java.util.Set;

public interface ScenarioManager {

    Set<String> getScenarios();

    void startScenario(String name);

}
