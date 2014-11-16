package nl.tno.fpai.monitoring.dummy;

import java.util.Date;
import java.util.Map;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ext.AbstractObservationProvider;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.osgi.framework.ServiceRegistration;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.metatype.Configurable;

/**
 * Observation Provider with some dummy values ...
 */
@Component(immediate = true, designateFactory = DummyObservationProvider.Config.class)
public class DummyObservationProvider extends AbstractObservationProvider<SomeValues> {
    public interface Config {
        String identifier();
    }

    private Thread thread;
    private ServiceRegistration<?> serviceRegistration;

    @Activate
    public void activate(Map<String, Object> properties) {
        Config config = Configurable.createConfigurable(Config.class, properties);
        int randomId = (int) (Math.random() * 10000);
        String by = "observer-" + config.identifier() + "-" + randomId;
        String of = "somthing-" + randomId;
        serviceRegistration = new ObservationProviderRegistrationHelper(this).observationOf(of)
                                                                             .observedBy(by)
                                                                             .observationType(SomeValues.class)
                                                                             .register();

        thread = new Thread("thread-for-observation-provider-" + by) {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep((long) (10 + Math.random() * 10));
                    } catch (InterruptedException e) {
                    }

                    DummyObservationProvider.this.publish(new Observation<SomeValues>(new Date(), new SomeValues() {
                        double v1 = Math.random() * 10 + 10;
                        String v2 = Integer.toHexString((int) (Math.random() * 10000));

                        @Override
                        public String getValue2() {
                            return v2;
                        }

                        @Override
                        public double getValue1() {
                            return v1;
                        }

                        @Override
                        public Measurable<Power> getValue3() {
                            return Measure.valueOf(v1, SI.WATT);
                        }
                    }));
                }
            }
        };
        thread.start();
    }

    @Deactivate
    public void deactivate() {
        serviceRegistration.unregister();

        try {
            thread.interrupt();
            thread.join();
        } catch (InterruptedException e) {
        } finally {
            thread = null;
        }
    }
}
