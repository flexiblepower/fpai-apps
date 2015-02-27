package nl.tno.fpai.monitoring.dummy;

import java.util.Date;
import java.util.Map;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationProvider;
import org.flexiblepower.observation.ext.AbstractObservationProvider;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.metatype.Configurable;

/**
 * Observation Provider with some dummy values ...
 */
@Component(immediate = true, designateFactory = DummyObservationProvider.Config.class)
public class DummyObservationProvider extends AbstractObservationProvider<SomeValues> {
    private static final int TIMEOUT = 5000;

    /** Configuration specification of the component. */
    public interface Config {
        String identifier();
    }

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private Thread thread;
    private ServiceRegistration<?> serviceRegistration;

    /**
     * Activates the component: registers an {@link ObservationProvider} and starts a thread which publishes every so
     * often.
     */
    @Activate
    public void activate(Map<String, Object> properties) {
        Config config = Configurable.createConfigurable(Config.class, properties);
        int randomId = (int) (Math.random() * 10000);
        String by = "observer-" + config.identifier() + "-" + randomId;
        String of = "something-" + randomId;
        serviceRegistration = new ObservationProviderRegistrationHelper(this).observationOf(of)
                                                                             .observedBy(by)
                                                                             .observationType(SomeValues.class)
                                                                             .register();

        thread = new Thread("thread-for-observation-provider-" + by) {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(500); // (long) (TIMEOUT + Math.random() * TIMEOUT));
                    } catch (InterruptedException e) {
                        // stop at interrupt
                        break;
                    }

                    DummyObservationProvider.this.publish(new Observation<SomeValues>(new Date(), new SomeValues() {
                        private final double v1 = Math.random() * 10000;
                        private final String v2 = Integer.toHexString((int) v1);

                        @Override
                        public SomeOtherValues getSomeOtherValues() {
                            return new SomeOtherValues() {
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
                            };
                        }

                        @Override
                        public double getValue1() {
                            return v1;
                        }

                        @Override
                        public String getObserverId() {
                            return v2;
                        }

                        @Override
                        public long getTimestamp() {
                            return (long) v1;
                        }
                    }));
                }
            }
        };
        thread.start();
    }

    /** Deactivates the component: de-registers the observation provider, and stops the thread. */
    @Deactivate
    public void deactivate() {
        try {
            serviceRegistration.unregister();
        } catch (Exception e) {
            logger.warn("An exception occurred while unregistering the Observation Provider", e);
        }

        try {
            thread.interrupt();
            thread.join();
        } catch (InterruptedException e) {
            logger.warn("Interrupting and stopping the thread was interrupted unexpectedly.", e);
        } finally {
            thread = null;
        }
    }
}
