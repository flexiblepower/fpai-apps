package org.flexiblepower.miele.refrigerator.driver;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.protocol.mielegateway.api.ActionPerformer;
import org.flexiblepower.protocol.mielegateway.api.MieleResourceDriverFactory;
import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorControlParameters;
import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorState;
import org.osgi.framework.BundleContext;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;

@Component(provide = MieleResourceDriverFactory.class)
public class RefrigeratorDriverFactory extends
                                      MieleResourceDriverFactory<RefrigeratorState, RefrigeratorControlParameters, RefrigeratorDriver> {

    public RefrigeratorDriverFactory() {
        super(RefrigeratorState.class);
    }

    private FlexiblePowerContext context;

    @Reference
    public void setContext(FlexiblePowerContext context) {
        this.context = context;
    }

    private BundleContext bundleContext;

    @Activate
    public void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Override
    public boolean canHandleType(String type) {
        return "???????????????".equals(type);
    }

    @Override
    public RefrigeratorDriver create(ActionPerformer actionPerformer) {
        return new RefrigeratorDriver(actionPerformer, context, bundleContext);
    }
}
