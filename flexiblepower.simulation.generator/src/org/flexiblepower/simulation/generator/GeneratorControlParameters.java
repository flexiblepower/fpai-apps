package org.flexiblepower.simulation.generator;

import org.flexiblepower.ral.ResourceControlParameters;

public interface GeneratorControlParameters extends ResourceControlParameters {
    GeneratorLevel getLevel();
}
