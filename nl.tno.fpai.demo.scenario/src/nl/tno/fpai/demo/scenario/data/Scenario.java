package nl.tno.fpai.demo.scenario.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Scenario {
    public static class Builder {
        private final Map<String, IdSet> idSets;
        private final List<ScenarioConfiguration> configurations;
        private String name;

        public Builder() {
            idSets = new HashMap<String, IdSet>();
            configurations = new ArrayList<ScenarioConfiguration>();
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder addIdSet(IdSet idSet) {
            idSets.put(idSet.getName(), idSet);
            return this;
        }

        public Builder addConfiguration(ScenarioConfiguration configuration) {
            configurations.add(configuration);
            return this;
        }

        public Scenario build() {
            return new Scenario(name, idSets, configurations);
        }
    }

    private final String name;
    private final Map<String, IdSet> idSets;
    private final List<ScenarioConfiguration> configurations;

    public Scenario(String name, Map<String, IdSet> idSets, List<ScenarioConfiguration> configurations) {
        this.name = name;
        this.idSets = Collections.unmodifiableMap(new HashMap<String, IdSet>(idSets));
        this.configurations = Collections.unmodifiableList(new ArrayList<ScenarioConfiguration>(configurations));
    }

    public String getName() {
        return name;
    }

    public Map<String, IdSet> getIdSets() {
        return idSets;
    }

    public List<ScenarioConfiguration> getConfigurations() {
        return configurations;
    }
}
