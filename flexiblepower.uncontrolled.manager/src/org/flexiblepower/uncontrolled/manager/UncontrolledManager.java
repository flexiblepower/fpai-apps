package org.flexiblepower.uncontrolled.manager;

import java.util.Date;
import java.util.Map;

import javax.measure.Measure;
import javax.measure.quantity.Energy;
import javax.measure.unit.SI;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationProvider;
import org.flexiblepower.rai.Allocation;
import org.flexiblepower.rai.UncontrolledLGControlSpace;
import org.flexiblepower.rai.values.EnergyProfile;
import org.flexiblepower.ral.ResourceManager;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrolledControlParameters;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrolledDriver;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrolledState;
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.time.TimeService;
import org.flexiblepower.uncontrolled.manager.UncontrolledManager.Config;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = ResourceManager.class)
public class UncontrolledManager extends
                                AbstractResourceManager<UncontrolledLGControlSpace, UncontrolledState, UncontrolledControlParameters> {

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "uncontrolled", description = "Resource identifier")
        String resourceId();

        @Meta.AD(deflt = "20", description = "Expiration of the ControlSpaces [s]", required = false)
        int expirationTime();
    }

    private Config config;

    public UncontrolledManager() {
        super(UncontrolledDriver.class, UncontrolledLGControlSpace.class);
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    @Activate
    public void activate(Map<String, String> properties) {
        config = Configurable.createConfigurable(Config.class, properties);
    };

    @Override
    public void handleAllocation(Allocation allocation) {
        // nothing to handle
    }

    @Override
    public void consume(ObservationProvider<? extends UncontrolledState> source,
                        Observation<? extends UncontrolledState> observation) {
        UncontrolledState uncontrolledState = observation.getValue();
        logger.debug("Receved demand from " + source + " of " + uncontrolledState.getDemand());
        publish(constructControlSpace(observation.getValue()));
    }

    private UncontrolledLGControlSpace constructControlSpace(UncontrolledState uncontrolledState) {
        Measure<Double, Energy> energy = Measure.valueOf(uncontrolledState.getDemand().doubleValue(SI.WATT) * config.expirationTime(),
                                                         SI.JOULE);
        EnergyProfile energyProfile = EnergyProfile.create()
                                                   .add(Measure.valueOf(config.expirationTime(), SI.SECOND), energy)
                                                   .build();
        Date now = timeService.getTime();
        return new UncontrolledLGControlSpace(config.resourceId(), now, energyProfile);
    }
}
