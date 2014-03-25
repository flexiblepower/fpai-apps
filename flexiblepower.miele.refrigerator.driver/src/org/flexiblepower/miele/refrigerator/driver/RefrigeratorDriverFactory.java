package org.flexiblepower.miele.refrigerator.driver;

import org.flexiblepower.protocol.mielegateway.api.MieleResourceDriverFactory;
import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorControlParameters;
import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorState;

public class RefrigeratorDriverFactory extends
                                      MieleResourceDriverFactory<RefrigeratorState, RefrigeratorControlParameters, RefrigeratorDriver> {

    public RefrigeratorDriverFactory() {
        super(RefrigeratorState.class);
    }

    @Override
    public boolean canHandleType(String type) {
        return "MieleRE".equals(type) || "???????????????".equals(type);
    }

    @Override
    public RefrigeratorDriver create() {
        return new RefrigeratorDriver();
    }
}
