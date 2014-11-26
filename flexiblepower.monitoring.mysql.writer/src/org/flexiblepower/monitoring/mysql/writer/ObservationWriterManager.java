package org.flexiblepower.monitoring.mysql.writer;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.sql.DataSource;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationProvider;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
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
     * Configuration for the manager of observation writers which allows for expressing LDAP filters used to selectively
     * reference observation providers (instead of all providers) and to select the right data source for writing the
     * observations to.
     */
    public interface Config {
        /** @return The LDAP filter used for discovery of ObservationProviders. */
        @AD(description = "LDAP filter for discovery of ObservationProviders", deflt = "")
        String provider_filter();

        /** @return The LDAP filter used for selection of the DataSource used for writing the observations to. */
        @AD(description = "LDAP filter for binding to the right data source", deflt = "")
        String dataSource_filter();
    }

    /** The index of the column with table names from {@link DatabaseMetaData#getTables}. */
    private static final int TABLE_NAME_COLUMN_IDX = 3;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /** The data source used by the observation writers */
    private DataSource dataSource;
    /** map of writers keyed by the providers they were created for */
    private final Map<ObservationProvider, ObservationWriter> writers = new ConcurrentHashMap<ObservationProvider, ObservationWriter>();

    /** The thread pool for executing the inserts */
    private final Executor executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    /**
     * Activates the ObservationWriterManager. Make sure a dataSource is configured (see
     * {@link #setDataSource(DataSource)}. The activation ensures that the right database structure is in place.
     */
    @Activate
    public void activate() {
        // TODO do this asynchronously!
        // TODO ensure that the date dimension is maintained ... automatically

        // new Thread() {
        // public void run() {
        try {
            ensureSchema();
        } catch (Exception e) {
            logger.error("Could not ensure the correct schema required", e);
        }
        // }
        // }.start();
    }

    private void ensureSchema() throws SQLException, IOException {
        Connection con = dataSource.getConnection();

        try {
            ResultSet tables = con.getMetaData().getTables(null, null, "dim_%", new String[] { "TABLE" });
            Set<String> dimensionTables = new HashSet<String>();

            try {
                // loop over the tables listed
                while (tables.next()) {
                    dimensionTables.add(tables.getString(TABLE_NAME_COLUMN_IDX));
                }
            } finally {
                tables.close();
            }

            if (!dimensionTables.contains("dim_observer")) {
                createDimObserver(con);
            }

            if (!dimensionTables.contains("dim_time")) {
                createDimTime(con);

                Thread populateDimTime = new Thread() {
                    @Override
                    public void run() {
                        try {
                            Connection con = dataSource.getConnection();

                            try {
                                populateDimTime(con);
                            } finally {
                                con.close();
                            }
                        } catch (SQLException e) {
                            logger.warn("Failed to populate table dim_time", e);
                        }
                    };
                };

                populateDimTime.start();
            }

            if (!dimensionTables.contains("dim_date")) {
                createDimDate(con);

                Thread populateDimDate = new Thread() {
                    @Override
                    public void run() {
                        try {
                            Connection con = dataSource.getConnection();

                            try {
                                populateDimDate(con);
                            } finally {
                                con.close();
                            }
                        } catch (SQLException e) {
                            logger.warn("Failed to populate table dim_date", e);
                        }
                    };
                };

                populateDimDate.start();
            }

            logger.info("Schema checked and created and populated all required tables where necessary");
        } finally {
            con.close();
        }
    }

    private void createDimObserver(Connection con) throws SQLException {
        logger.info("Creating table dim_observer");

        Statement stmt = con.createStatement();

        try {
            stmt.execute(

                "CREATE TABLE `dim_observer` (\n" + "  `id` bigint(20) NOT NULL AUTO_INCREMENT,\n"
                        + "  `observedBy` varchar(255) NOT NULL,\n"
                        + "  `observationOf` varchar(255) NOT NULL,\n"
                        + "  `type` text NOT NULL,\n"
                        + "  PRIMARY KEY (`id`),\n"
                        + "  UNIQUE INDEX `unique_observer` (`observationOf` ASC, `observedBy` ASC)\n"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8;"

                );
        } finally {
            stmt.close();
        }
    }

    private void createDimTime(Connection con) throws SQLException {
        logger.info("Creating table dim_time");

        Statement stmt = con.createStatement();

        try {
            stmt.execute(

                "CREATE TABLE `dim_time` (\n" + "  `time` time NOT NULL,\n"
                        + "  `hour` int(2) unsigned NOT NULL,\n"
                        + "  `minute` int(2) unsigned NOT NULL,\n"
                        + "  PRIMARY KEY (`time`)\n"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8;"

                );
        } finally {
            stmt.close();
        }
    }

    private void populateDimTime(Connection con) throws SQLException {
        logger.info("Populating table dim_time");

        PreparedStatement stmt = con.prepareStatement("INSERT INTO `dim_time` (`time`, `hour`, `minute`) VALUES (?,?,?);");

        try {
            int h = 0, m = 0;

            while (h < 24) {
                int idx = 1;
                stmt.setString(idx++, String.format("%02d:%02d:00", h, m));
                stmt.setInt(idx++, h);
                stmt.setInt(idx++, m);
                stmt.addBatch();

                if (++m >= 60) {
                    m = 0;
                    h++;
                }
            }

            assert stmt.executeBatch().length == 1440;
        } finally {
            stmt.close();
        }
    }

    private void createDimDate(Connection con) throws SQLException {
        logger.info("Creating table dim_date");

        Statement stmt = con.createStatement();

        try {
            stmt.execute(

                "CREATE TABLE `dim_date` (\n" + "  `date` date NOT NULL,\n"
                        + "  `year` int(11) unsigned NOT NULL,\n"
                        + "  `quarter` int(1) unsigned NOT NULL,\n"
                        + "  `month` int(2) unsigned NOT NULL,\n"
                        + "  `week` int(2) unsigned NOT NULL,\n"
                        + "  `day_of_year` int(3) unsigned NOT NULL,\n"
                        + "  `day_of_month` int(2) unsigned NOT NULL,\n"
                        + "  `day_of_week` int(1) unsigned NOT NULL,\n"
                        + "  PRIMARY KEY (`date`)\n"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8;"

                );
        } finally {
            stmt.close();
        }
    }

    private void populateDimDate(Connection con) throws SQLException {
        logger.info("Populating table dim_date");

        PreparedStatement stmt = con.prepareStatement("INSERT INTO `dim_date` (`date`, `year`, `quarter`, `month`, `week`, `day_of_year`, `day_of_month`, `day_of_week`) VALUES (?,?,?,?,?,?,?,?);");

        try {
            TimeZone utc = TimeZone.getTimeZone("UTC");

            GregorianCalendar now = new GregorianCalendar(utc);

            GregorianCalendar d = new GregorianCalendar(utc);
            d.clear();
            d.set(Calendar.YEAR, now.get(Calendar.YEAR));

            GregorianCalendar end = new GregorianCalendar(utc);
            end.clear();
            end.set(Calendar.YEAR, now.get(Calendar.YEAR) + 5);

            while (d.before(end)) {
                int idx = 1;
                stmt.setObject(idx++, d.getTime());
                stmt.setInt(idx++, d.get(Calendar.YEAR));
                stmt.setInt(idx++, (int) (Math.floor(d.get(Calendar.MONTH)) / 3 + 1));
                stmt.setInt(idx++, d.get(Calendar.MONTH) + 1);
                stmt.setInt(idx++, d.get(Calendar.WEEK_OF_YEAR));
                stmt.setInt(idx++, d.get(Calendar.DAY_OF_YEAR));
                stmt.setInt(idx++, d.get(Calendar.DAY_OF_MONTH));
                stmt.setInt(idx++, d.get(Calendar.DAY_OF_WEEK));

                stmt.addBatch();

                d.add(Calendar.DAY_OF_MONTH, 1);
            }

            stmt.executeBatch();
        } finally {
            stmt.close();
        }
    }

    /**
     * Configures the data source to be used by the observation writers managed by this component.
     *
     * @param dataSource
     *            The data source to be used.
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

            ObservationWriter old = writers.put(provider, w);
            if (old != null) {
                old.deactivate();
            }

            w.setDataSource(dataSource);
            w.setExecutor(executor);
            w.setProvider(properties, provider);
            w.activate();
        } catch (Exception e) {
            logger.error("Couldn't reference a provider", e);
        }
    }

    /**
     * Remove a provider, in response the observation writer corresponding to the provider is deactivated.
     *
     * @param provider
     *            The observation provider that was deactivated.
     */
    public void removeProvider(ObservationProvider provider) {
        ObservationWriter w = writers.remove(provider);
        if (w != null) {
            w.deactivate();
        }
    }
}
