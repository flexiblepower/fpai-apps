package org.flexiblepower.miele.refrigerator.driver;

import org.flexiblepower.protocol.mielegateway.api.ActionPerformer;
import org.flexiblepower.protocol.mielegateway.api.MieleResourceDriverFactory;
import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorControlParameters;
import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorState;
import org.flexiblepower.time.TimeService;

import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;

@Component(provide = MieleResourceDriverFactory.class)
public class RefrigeratorDriverFactory extends
                                      MieleResourceDriverFactory<RefrigeratorState, RefrigeratorControlParameters, RefrigeratorDriver> {

    public RefrigeratorDriverFactory() {
        super(RefrigeratorState.class);
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    @Override
    public boolean canHandleType(String type) {
        return "MieleRE".equals(type) || "???????????????".equals(type);
    }

    @Override
    public RefrigeratorDriver create(ActionPerformer actionPerformer) {
        return new RefrigeratorDriver(actionPerformer, timeService);
    }
}
