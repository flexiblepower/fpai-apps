package org.flexiblepower.simulation.generator;

import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measure;
import javax.measure.unit.SI;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.driver.generator.GeneratorControlParameters;
import org.flexiblepower.driver.generator.GeneratorLevel;
import org.flexiblepower.driver.generator.GeneratorState;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.simulation.generator.GeneratorSimulation.Config;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Modified;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Port(name = "manager", accepts = GeneratorControlParameters.class, sends = GeneratorState.class)
@Component(designateFactory = Config.class, provide = Endpoint.class, immediate = true)
public class GeneratorSimulation extends AbstractResourceDriver<GeneratorState, GeneratorControlParameters> implements
                                                                                                           Runnable {
    private static final Logger logger = LoggerFactory.getLogger(GeneratorSimulation.class);

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "generator", description = "Resource identifier")
        String resourceId();

        @Meta.AD(deflt = "5", description = "Frequency with which updates will be sent out in seconds")
        int updateFrequency();
    }

    private ScheduledFuture<?> scheduledFuture;
    private Config config;
    private GeneratorLevel generatorLevel = new GeneratorLevel();

    class State implements GeneratorState {
        private final GeneratorLevel generatorLevel;

        public State(GeneratorLevel generatorLevel) {
            this.generatorLevel = generatorLevel;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public GeneratorLevel getGeneratorLevel() {
            return generatorLevel;
        }

    }

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        try {
            config = Configurable.createConfigurable(Config.class, properties);

            scheduledFuture = fpContext.scheduleAtFixedRate(this,
                                                            Measure.valueOf(0, SI.SECOND),
                                                            Measure.valueOf(config.updateFrequency(), SI.SECOND));
            generatorLevel.setLevel(0);

        } catch (RuntimeException ex) {
            logger.error("Error during initialization of the generator simulation: " + ex.getMessage(), ex);
            deactivate();
            throw ex;
        }
    }

    @Modified
    public void modify(BundleContext bundleContext, Map<String, Object> properties) {
        try {
            config = Configurable.createConfigurable(Config.class, properties);

        } catch (RuntimeException ex) {
            logger.error("Error during modification of the generator simulation: " + ex.getMessage(), ex);
            deactivate();
            throw ex;
        }
    }

    @Deactivate
    public void deactivate() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
    }

    @Override
    public void run() {
        GeneratorState state = new State(generatorLevel);
        logger.debug("Publishing state {}", state);
        // System.out.println("ping");
        publishState(state);
    }

    private FlexiblePowerContext fpContext;

    @Reference
    public void setContext(FlexiblePowerContext fpContext) {
        this.fpContext = fpContext;
    }

    @Override
    protected void handleControlParameters(GeneratorControlParameters controlParameters) {
        generatorLevel = controlParameters.getLevel();
    }

    public GeneratorLevel getGeneratorLevel() {
        return generatorLevel;
    }
}
