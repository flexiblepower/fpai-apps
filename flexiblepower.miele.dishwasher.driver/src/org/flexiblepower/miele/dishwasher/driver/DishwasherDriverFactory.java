package org.flexiblepower.miele.dishwasher.driver;

import org.flexiblepower.protocol.mielegateway.api.MieleResourceDriverFactory;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherControlParameters;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;

import aQute.bnd.annotation.component.Component;

@Component(provide = MieleResourceDriverFactory.class)
public class DishwasherDriverFactory extends
                                    MieleResourceDriverFactory<DishwasherState, DishwasherControlParameters, DishwasherDriver> {

    public DishwasherDriverFactory() {
        super(DishwasherState.class);
    }

    @Override
    public boolean canHandleType(String type) {
        return "DW_G1000".equals(type);
    }

    @Override
    public DishwasherDriver create() {
        return new DishwasherDriver();
    }
}
