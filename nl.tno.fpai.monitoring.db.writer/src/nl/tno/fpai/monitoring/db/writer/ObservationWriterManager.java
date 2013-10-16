package nl.tno.fpai.monitoring.db.writer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.sql.DataSource;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationProvider;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.ConfigurationPolicy;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Meta.AD;

/**
 * OSGi Declarative Services Component which manages {@link ObservationWriter}s which take {@link Observation}s from
 * {@link ObservationProvider}s and write them to a database. For each provider referenced by this manager, an
 * observation writer is created. When a provider is no longer referenced, the observation writer is deactivated.
 */
@SuppressWarnings("rawtypes")
@Component(immediate = true,
           configurationPolicy = ConfigurationPolicy.optional,
           designate = ObservationWriterManager.Config.class)
public class ObservationWriterManager {
    /**
     * Configuration for the manager of observation writers which allows for expressing an LDAP filter used to
     * selectively reference observation providers (instead of all providers)
     */
    public interface Config {
        @AD(description = "LDAP filter for discovery of ObservationProviders", deflt = "")
        String provider_filter();
    }

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    /** The data source used by the observation writers */
    private DataSource dataSource;
    /** map of writers keyed by the providers they were created for */
    private Map<ObservationProvider, ObservationWriter> writers = new ConcurrentHashMap<ObservationProvider, ObservationWriter>();

    /** The thread pool for executing the inserts */
    private Executor executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    /**
     * Configures the data source to be used by the observation writers managed by this component.
     */
    @Reference
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Adds a provider as a reference, in response an observation writer will be activated to consume the observations
     * from the provider and write them to a database.
     * 
     * @param provider
     *            The observation provider to take observations from.
     * @param properties
     *            The properties which describe the provider as per the org.flexiblepower.observation specifications
     *            (see also {@link ObservationProviderRegistrationHelper}).
     */
    @Reference(dynamic = true, multiple = true, optional = true)
    public void addProvider(ObservationProvider provider, Map<String, Object> properties) {
        try {
            ObservationWriter w = new ObservationWriter();

            ObservationWriter old = this.writers.put(provider, w);
            if (old != null) {
                old.deactivate();
            }

            w.setDataSource(this.dataSource);
            w.setExecutor(this.executor);
            w.setProvider(properties, provider);
            w.activate();
        } catch (Exception e) {
            logger.error("Couldn't reference a provider", e);
        }
    }

    /**
     * Remove a provider, in response the observation writer corresponding to the provider is deactivated.
     */
    public void removeProvider(ObservationProvider provider) {
        ObservationWriter w = this.writers.remove(provider);
        if (w != null) {
            w.deactivate();
        }
    }
}
