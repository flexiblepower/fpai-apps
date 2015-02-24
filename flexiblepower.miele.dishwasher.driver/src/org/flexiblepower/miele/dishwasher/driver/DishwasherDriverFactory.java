package org.flexiblepower.miele.dishwasher.driver;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.protocol.mielegateway.api.ActionPerformer;
import org.flexiblepower.protocol.mielegateway.api.MieleResourceDriverFactory;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherControlParameters;
import org.flexiblepower.ral.drivers.dishwasher.DishwasherState;
import org.osgi.framework.BundleContext;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;

@Component(provide = MieleResourceDriverFactory.class)
public class DishwasherDriverFactory extends
                                    MieleResourceDriverFactory<DishwasherState, DishwasherControlParameters, DishwasherDriver> {

    public DishwasherDriverFactory() {
        super(DishwasherState.class);
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
        return "DW_G1000".equals(type) || "G5985          ".equals(type);
    }

    @Override
    public DishwasherDriver create(ActionPerformer actionPerformer) {
        return new DishwasherDriver(actionPerformer, context, bundleContext);
    }
}
