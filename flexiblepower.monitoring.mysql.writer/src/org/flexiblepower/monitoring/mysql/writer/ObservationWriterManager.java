package org.flexiblepower.monitoring.mysql.writer;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
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

import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.unit.NonSI;
import javax.sql.DataSource;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.monitoring.mysql.ui.HttpActivator;
import org.flexiblepower.monitoring.mysql.ui.SQLServlet;
import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationProvider;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta.AD;
import aQute.bnd.annotation.metatype.Meta.OCD;

import com.mysql.jdbc.Driver;

/**
 * OSGi Declarative Services Component which manages {@link ObservationWriter}s which take {@link Observation}s from
 * {@link ObservationProvider}s and write them to a database. For each provider referenced by this manager, an
 * observation writer is created. When a provider is no longer referenced, the observation writer is deactivated.
 */
@SuppressWarnings("rawtypes")
@Component(immediate = true, designate = ObservationWriterManager.Config.class)
public class ObservationWriterManager {
    private static final Logger logger = LoggerFactory.getLogger(ObservationWriterManager.class);

    static {
        try {
            logger.debug("Loading Mysql driver for class {}", new Driver());
        } catch (SQLException e) {
            logger.error("Could not start a Mysql driver: " + e.getMessage(), e);
        }
    }

    /**
     * Configuration for the manager of observation writers which allows for expressing LDAP filters used to selectively
     * reference observation providers (instead of all providers) and to select the right data source for writing the
     * observations to.
     */
    @OCD(name = "MySQL observation writer")
    public interface Config {
        /** @return The LDAP filter used for discovery of ObservationProviders. */
        @AD(description = "LDAP filter for discovery of ObservationProviders", deflt = "")
        String provider_filter();

        @AD(deflt = "jdbc:mysql://localhost:3306/project", description = "URL to the database")
        String jdbcURL();

        @AD(deflt = "fpai", description = "username for connecting to the database")
        String jdbcUser();

        @AD(deflt = "fpai", description = "password for connecting to the database")
        String jdbcPassword();

        @AD(deflt = "5", min = "1", max = "60", description = "The number of minutes between update writes")
        int updateRate();
    }

    /** The index of the column with table names from {@link DatabaseMetaData#getTables}. */
    private static final int TABLE_NAME_COLUMN_IDX = 3;

    /** map of writers keyed by the providers they were created for */
    private final Map<ObservationProvider, ObservationWriter> writers = new ConcurrentHashMap<ObservationProvider, ObservationWriter>();

    private FlexiblePowerContext context;

    @Reference
    public void setContext(FlexiblePowerContext context) {
        this.context = context;
    }

    private Config config;

    private Measure<Integer, Duration> updateRate;

    private HttpActivator activator;

    /**
     * Activates the ObservationWriterManager. Make sure a dataSource is configured (see
     * {@link #setDataSource(DataSource)}. The activation ensures that the right database structure is in place.
     *
     * @throws Exception
     */
    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) throws Exception {
        config = Configurable.createConfigurable(Config.class, properties);

        try {
            activator = new HttpActivator(bundleContext, new SQLServlet(this));
        } catch (NoClassDefFoundError error) {
            // Ignore, the servlet is optional
            logger.info("Could not load the servlet, no webserver available?");
        }

        updateRate = Measure.valueOf(config.updateRate(), NonSI.MINUTE);

        // TODO do this asynchronously!
        // TODO ensure that the date dimension is maintained ... automatically

        // new Thread() {
        // public void run() {
        try {
            ensureSchema();
        } catch (Exception e) {
            logger.error("Could not ensure the correct schema required, are the database settings correct?", e);
            throw e;
        }
        // }
        // }.start();
    }

    public void deactivate() {
        if (activator != null) {
            activator.close();
            activator = null;
        }
    }

    private void ensureSchema() throws SQLException, IOException {
        Connection con = createConnection();

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

                context.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Connection con = createConnection();

                            try {
                                populateDimTime(con);
                            } finally {
                                con.close();
                            }
                        } catch (SQLException e) {
                            logger.warn("Failed to populate table dim_time", e);
                        }
                    };
                });
            }

            if (!dimensionTables.contains("dim_date")) {
                createDimDate(con);

                context.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Connection con = createConnection();

                            try {
                                populateDimDate(con);
                            } finally {
                                con.close();
                            }
                        } catch (SQLException e) {
                            logger.warn("Failed to populate table dim_date", e);
                        }
                    };
                });
            }

            logger.info("Schema checked and created and populated all required tables where necessary");
        } finally {
            con.close();
        }
    }

    public Connection createConnection() throws SQLException {
        return DriverManager.getConnection(config.jdbcURL(), config.jdbcUser(), config.jdbcPassword());
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
            ObservationWriter w = new ObservationWriter(context, this, provider, properties, updateRate);

            ObservationWriter old = writers.put(provider, w);
            if (old != null) {
                old.close();
            }
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
            w.close();
        }
    }
}
