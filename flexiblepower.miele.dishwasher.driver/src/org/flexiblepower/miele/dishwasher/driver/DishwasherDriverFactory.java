package org.flexiblepower.miele.dishwasher.driver;

import org.flexiblepower.protocol.mielegateway.api.ActionPerformer;
import org.flexiblepower.protocol.mielegateway.api.MieleResourceDriverFactory;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherControlParameters;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;
import org.flexiblepower.time.TimeService;

import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;

@Component(provide = MieleResourceDriverFactory.class)
public class DishwasherDriverFactory extends
                                    MieleResourceDriverFactory<DishwasherState, DishwasherControlParameters, DishwasherDriver> {

    public DishwasherDriverFactory() {
        super(DishwasherState.class);
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    @Override
    public boolean canHandleType(String type) {
        return "DW_G1000".equals(type) || "G5985".equals(type);
    }

    @Override
    public DishwasherDriver create(ActionPerformer actionPerformer) {
        return new DishwasherDriver(actionPerformer, timeService);
    }
}
