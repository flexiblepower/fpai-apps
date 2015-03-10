package net.powermatcher.fpai.peakshaving;

import net.powermatcher.core.concentrator.TransformingConcentrator;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationConsumer;
import org.flexiblepower.observation.ObservationProvider;
import org.flexiblepower.ral.drivers.uncontrolled.PowerState;

import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Meta;

@Component(immediate = true, designateFactory = TransformingConcentratorInformer.Config.class)
public class TransformingConcentratorInformer implements ObservationConsumer<PowerState> {
    public interface Config {
        @Meta.AD(deflt = "(agentId=peakshavingconcentrator)",
                 description = "The filter that is used to determine which transforming concentrator should get the power values")
                String
                concentrator_filter();

        @Meta.AD(deflt = "(org.flexiblepower.monitoring.observationOf=something)",
                 description = "The filter that is used to determine which observation provider should be used to get the power values")
                String
                observationProvider_filter();
    }

    private TransformingConcentrator concentrator;

    @Reference
    public void setConcentrator(TransformingConcentrator concentrator) {
        this.concentrator = concentrator;
    }

    @Reference
    public void setObservationProvider(ObservationProvider<PowerState> provider) {
        provider.subscribe(this);
    }

    public void unsetObservationProvider(ObservationProvider<PowerState> provider) {
        provider.unsubscribe(this);
    }

    @Override
    public void consume(ObservationProvider<? extends PowerState> source,
                        Observation<? extends PowerState> observation) {
        concentrator.setMeasuredFlow(observation.getValue().getCurrentUsage());
    }
}
