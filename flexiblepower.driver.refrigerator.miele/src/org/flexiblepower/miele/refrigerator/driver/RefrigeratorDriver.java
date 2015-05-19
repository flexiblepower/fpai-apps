package org.flexiblepower.miele.refrigerator.driver;

import java.util.Map;

import javax.measure.Measurable;
import javax.measure.quantity.Temperature;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.protocol.mielegateway.api.ActionPerformer;
import org.flexiblepower.protocol.mielegateway.api.ActionResult;
import org.flexiblepower.protocol.mielegateway.api.MieleResourceDriver;
import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorControlParameters;
import org.flexiblepower.ral.drivers.refrigerator.RefrigeratorState;
import org.flexiblepower.ui.Widget;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RefrigeratorDriver extends MieleResourceDriver<RefrigeratorState, RefrigeratorControlParameters> implements
                                                                                                             org.flexiblepower.ral.drivers.refrigerator.RefrigeratorDriver
{
    private static final Logger logger = LoggerFactory.getLogger(RefrigeratorDriver.State.class);

    static final class State implements RefrigeratorState {

        private final boolean isConnected;
        private final Measurable<Temperature> currentTemperature;
        private final Measurable<Temperature> targetTemperature;
        private final Measurable<Temperature> minimumTemperature;
        private final boolean supercoolMode;

        public State(boolean isConnected,
                     Measurable<Temperature> currentTemperature,
                     Measurable<Temperature> targetTemperature,
                     Measurable<Temperature> minimumTemperature,
                     boolean supercoolMode) {
            this.isConnected = isConnected;
            this.currentTemperature = currentTemperature;
            this.targetTemperature = targetTemperature;
            this.minimumTemperature = minimumTemperature;
            this.supercoolMode = supercoolMode;
        }

        @Override
        public boolean isConnected() {
            return isConnected;
        }

        @Override
        public Measurable<Temperature> getCurrentTemperature() {
            return currentTemperature;
        }

        @Override
        public Measurable<Temperature> getTargetTemperature() {
            return targetTemperature;
        }

        @Override
        public Measurable<Temperature> getMinimumTemperature() {
            return minimumTemperature;
        }

        @Override
        public boolean getSupercoolMode() {
            return supercoolMode;
        }

    }

    private final RefrigeratorWidget widget;
    private final ServiceRegistration<Widget> widgetRegistration;

    public RefrigeratorDriver(ActionPerformer actionPerformer, FlexiblePowerContext context, BundleContext bundleContext) {
        super(actionPerformer, context);
        widget = new RefrigeratorWidget(this);
        widgetRegistration = bundleContext.registerService(Widget.class, widget, null);
    }

    @Override
    public void close() {
        super.close();
        widgetRegistration.unregister();
    }

    private volatile State currentState;

    @Override
    public void updateState(Map<String, String> information) {
        Measurable<Temperature> targetTemerature = parseTemperature(information.get("Target Temperature"));
        Measurable<Temperature> currentTemerature = parseTemperature(information.get("Current Temperature"));
        boolean supercoolMode = "Super Cooling".equals(information.get("State"));
        currentState = new State(true, targetTemerature, currentTemerature, null, supercoolMode);
        publishMieleState(currentState);
    }

    State getCurrentState() {
        return currentState;
    }

    @Override
    protected void handleControlParameters(RefrigeratorControlParameters controlParameters) {
        if (!currentState.supercoolMode && controlParameters.getSupercoolMode()) {
            // Turn supercoolMode on!
            logger.debug("Turning supercool mode on");
            ActionResult actionResult = performAction("SuperCooling On");
            logger.debug("Result of truning supercool mode on: " + actionResult.toString());
        } else if (currentState.supercoolMode && !controlParameters.getSupercoolMode()) {
            // Turn supercoolMode off!
            logger.debug("Turning supercool mode off");
            ActionResult actionResult = performAction("SuperCooling Off");
            logger.debug("Result of truning supercool mode off: " + actionResult.toString());
        } else {
            logger.debug("Received controlparameter with supercool = " + controlParameters.getSupercoolMode()
                         + ", but that already is the state, ignoring...");
        }
    }
}
