package org.flexiblepower.simulation.generator;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.simulation.generator.GeneratorSimulation.Config;
import org.flexiblepower.time.TimeService;
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
    private static final Logger log = LoggerFactory.getLogger(GeneratorSimulation.class);

    private java.util.NavigableSet<Integer> powerValues;

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "generator", description = "Resource identifier")
        String resourceId();

        @Meta.AD(deflt = "5", description = "Frequency with which updates will be sent out in seconds")
        int updateFrequency();
    }

    private ScheduledFuture<?> scheduledFuture;
    private ScheduledExecutorService schedulerService;
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

            scheduledFuture = schedulerService.scheduleAtFixedRate(this, 0, config.updateFrequency(), TimeUnit.SECONDS);
            generatorLevel.setLevel(0);

        } catch (RuntimeException ex) {
            log.error("Error during initialization of the generator simulation: " + ex.getMessage(), ex);
            deactivate();
            throw ex;
        }
    }

    @Modified
    public void modify(BundleContext bundleContext, Map<String, Object> properties) {
        try {
            config = Configurable.createConfigurable(Config.class, properties);

        } catch (RuntimeException ex) {
            log.error("Error during modification of the generator simulation: " + ex.getMessage(), ex);
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

    @Reference
    public void setSchedulerService(ScheduledExecutorService schedulerService) {
        this.schedulerService = schedulerService;
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    @Override
    public void run() {
        GeneratorState state = new State(generatorLevel);
        log.debug("Publishing state {}", state);
        // System.out.println("ping");
        publishState(state);
    }

    @Override
    protected void handleControlParameters(GeneratorControlParameters controlParameters) {
        generatorLevel = controlParameters.getLevel();
    }

    public GeneratorLevel getGeneratorLevel() {
        return generatorLevel;
    }
}
