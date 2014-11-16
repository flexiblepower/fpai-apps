package org.flexiblepower.monitoring.mysql.writer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.sql.DataSource;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationConsumer;
import org.flexiblepower.observation.ObservationProvider;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is capable of consuming {@link Observation}s from an {@link ObservationProvider} and write it to a
 * database.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class ObservationWriter implements ObservationConsumer {
    /** The maximum amount of observations to queue */
    private static final int QUEUE_SIZE = 16;

    /** The prefix of fact tables used in the database. */
    private static final String FACT_PREFIX = "fact_";

    /**
     * The prefix of keys in the observation provider's properties which indicate fields as part of the observations
     * (meta data).
     */
    private static final String FIELD_PREFIX = ObservationProviderRegistrationHelper.KEY_OBSERVATION_TYPE + ".";

    /** A map of of java classes to the SQL types to use. */
    @SuppressWarnings("serial")
    private static final Map<String, String> SQL_TYPE_MAP = Collections.unmodifiableMap(new HashMap<String, String>() {
        {
            // numbers
            put("boolean", "BIT");
            put("integer", "INTEGER");
            put("long", "BIGINT");
            put("float", "FLOAT");
            put("double", "DOUBLE");
            put("string", "TEXT");
            put(Boolean.class.getName(), "BIT");
            put(Integer.class.getName(), "INTEGER");
            put(Long.class.getName(), "BIGINT");
            put(Float.class.getName(), "FLOAT");
            put(Double.class.getName(), "DOUBLE");
            put(BigInteger.class.getName(), "BIGINT");
            put(BigDecimal.class.getName(), "DOUBLE"); // DECIMAL?
            put(Measurable.class.getName(), "DOUBLE"); // DECIMAL?
            // strings of bytes and chars
            put(byte[].class.getName(), "BLOB");
            put(String.class.getName(), "TEXT");
            // time and date
            put(java.util.Date.class.getName(), "TIMESTAMP");
            put(java.sql.Timestamp.class.getName(), "TIMESTAMP");
            put(java.sql.Time.class.getName(), "TIME");
            put(java.sql.Date.class.getName(), "DATE");
        }
    });

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /** The data source to use for getting connections to the database. */
    private DataSource dataSource;

    /** The provider of observations to write to the database. */
    private ObservationProvider provider;
    /** The meta-data properties of the provider. */
    private Map<String, Object> providerProperties;

    /** The (cached) names of the fields from {@link #providerProperties}, use {@link #getFieldNames()}. */
    private List<String> fieldNames;

    /** The (cached) insert query, use {@link #getInsertQuery()}. */
    private String insertQuery;

    /** The (cached) name of the fact table to write to, use {@link #getFactTableName()}. */
    private String factTableName;

    /** The (cached) id of the observer in the dim_obsever table, use {@link #lookupObserverId(Connection)}. */
    private Long observerId;

    /** The queue for writing observations to the data base */
    private final BlockingQueue<Observation> queue = new LinkedBlockingQueue<Observation>(QUEUE_SIZE);

    /** The executor used to perform inserts on */
    private Executor executor;

    /**
     * Set the data source to use for writing observations.
     */
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Set the executor to perform the inserts on
     */
    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    /**
     * Set the observation provider which sources the observations (this writer will subscribe as consumer to the given
     * provider).
     *
     * @param properties
     */
    public void setProvider(Map<String, Object> properties, ObservationProvider provider) {
        this.provider = provider;
        providerProperties = properties;
    }

    /**
     * Activates the writer, assumes that a data source and a provider are configured (see
     * {@link #setDataSource(DataSource)} and {@link #setProvider(ObservationProvider)}).
     */
    public void activate() {
        try {
            Connection con = dataSource.getConnection();

            try {
                // make sure a table exists to insert the observations into
                ensureTableExists(con);

                // lookup the observer id
                observerId = lookupObserverId(con);
                // or create one if there is no prior registration of this observer
                if (observerId == null) {
                    observerId = registerObserver(con);
                }
            } finally {
                con.close();
            }

            // subscribe to observations from the observation provider
            provider.subscribe(this);
        } catch (Throwable t) {
            logger.error("Couldn't activate an observation provider", t);
            // deactivate on failure
            deactivate();
        }
    }

    /**
     * Deactives the observation writer, the writer will unsubscribe from the observation provider configured for this
     * writer.
     */
    public void deactivate() {
        try {
            if (provider != null) {
                provider.unsubscribe(this);
            }
        } finally {
            provider = null;
            dataSource = null;
        }
    }

    /**
     * Checks if the fact table for storing the observations exists, and if not, creates the fact table.
     */
    private void ensureTableExists(Connection con) throws SQLException {
        if (doesFactTableExist(con, getFactTableName()) == false) {
            createFactTable(con);
        }

        // TODO check not only on type name, but on fields ???
    }

    /**
     * Determines whether the fact table for the type of observations coming from the associated observation provider is
     * available in the database.
     */
    private boolean doesFactTableExist(Connection con, String factTableName) throws SQLException {
        // lookup all fact tables
        // (searching on the exact fact table name didn't work with MySQL ...)
        ResultSet tables = con.getMetaData().getTables(null, null, FACT_PREFIX + "%", new String[] { "TABLE" });

        // loop over the tables listed
        while (tables.next()) {
            // return true of found (column three contains the table names)
            if (tables.getString(3).equals(factTableName)) {
                return true;
            }
        }

        // or return false if there weren't any tables with the right name.
        return false;
    }

    /**
     * Creates the fact table with observerId, time stamp (with millisecond precision), dateId, timeId, and any fields
     * as specified in the observation provider meta data. The table will reference the dim_observer, dim_time and
     * dim_date tables. observerId and time stamp are the primary key in the table and are to be unique.
     */
    private void createFactTable(Connection con) throws SQLException {
        // create a fact table
        StringBuilder create = new StringBuilder("CREATE TABLE IF NOT EXISTS `");
        create.append(getFactTableName());
        create.append("` (\n");

        // with the default fields
        create.append("  `observerId` bigint(20) NOT NULL,\n");
        create.append("  `timestamp` timestamp(3) NOT NULL DEFAULT 0,\n");
        create.append("  `dateId` date NOT NULL,\n");
        create.append("  `timeId` time NOT NULL,\n");

        // and the fields specific to the observation provider
        for (Map.Entry<String, Object> entry : getObservationTypeFields()) {
            create.append("  `");
            create.append(entry.getKey());
            create.append("` ");
            create.append(getSQLType((String) entry.getValue()));
            create.append(",\n");
        }

        // set the primary key
        create.append("  PRIMARY KEY (`observerId`,`timestamp`),\n");

        // index the observer id
        create.append("  KEY `");
        create.append(getFactTableName());
        create.append(".observer_idx` (`observerId`),\n");

        // index the date id
        create.append("  KEY `");
        create.append(getFactTableName());
        create.append(".date_idx` (`dateId`),\n");

        // index the time id
        create.append("  KEY `");
        create.append(getFactTableName());
        create.append(".time_idx` (`timeId`),\n");

        // reference the dim_observer table
        create.append("  CONSTRAINT `");
        create.append(getFactTableName());
        create.append(".observer` FOREIGN KEY (`observerId`) REFERENCES `dim_observer` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,\n");

        // reference the dim_date table
        create.append("  CONSTRAINT `");
        create.append(getFactTableName());
        create.append(".date` FOREIGN KEY (`dateId`) REFERENCES `dim_date` (`date`) ON DELETE NO ACTION ON UPDATE NO ACTION,\n");

        // reference the dim_time table
        create.append("  CONSTRAINT `");
        create.append(getFactTableName());
        create.append(".time` FOREIGN KEY (`timeId`) REFERENCES `dim_time` (`time`) ON DELETE NO ACTION ON UPDATE NO ACTION\n");

        // close the query
        create.append(");");

        Statement stmt = con.createStatement();

        try {
            stmt.executeUpdate(create.toString());
        } finally {
            stmt.close();
        }
    }

    /**
     * Provides an iterator over the fields in the meta-data of the observation provider which start with
     * {@value #FIELD_PREFIX}.
     */
    private Iterable<Entry<String, Object>> getObservationTypeFields() {
        // wrap all this in an Iterable
        return new Iterable<Entry<String, Object>>() {
            @Override
            public Iterator<Entry<String, Object>> iterator() {
                // create an Iterator over all properties
                // (which are to be filtered)
                final Iterator<Entry<String, Object>> entries = providerProperties.entrySet()
                                                                                  .iterator();

                // create an Iterator which works on the Iterator above and filters
                return new Iterator<Map.Entry<String, Object>>() {
                    Entry<String, Object> next = null;

                    @Override
                    public boolean hasNext() {
                        if (next != null) {
                            // if we already determined that there is a next field
                            return true;
                        } else {
                            // otherwise try and determine this and memorize the result
                            next = next1();
                            return next != null;
                        }
                    }

                    @Override
                    public Entry<String, Object> next() {
                        try {
                            if (next == null) {
                                // try to get the next field
                                next = next1();
                            }

                            if (next == null) {
                                // if no next field, we're at the end
                                throw new NoSuchElementException();
                            } else {
                                // return an entry with FIELD_PREFIX stripped
                                return new Entry<String, Object>() {
                                    String key = next.getKey().substring(FIELD_PREFIX.length());
                                    Object value = next.getValue();

                                    @Override
                                    public Object getValue() {
                                        return value;
                                    }

                                    @Override
                                    public String getKey() {
                                        return key;
                                    }

                                    @Override
                                    public Object setValue(Object value) {
                                        throw new UnsupportedOperationException();
                                    }
                                };
                            }
                        } finally {
                            // if we return, always loose the precomputed result
                            next = null;
                        }
                    }

                    private Entry<String, Object> next1() {
                        // loop over then entries Iterator until we find
                        // an entry of which the key starts with FIELD_PREFIX
                        while (entries.hasNext()) {
                            Entry<String, Object> e = entries.next();
                            if (e.getKey().startsWith(FIELD_PREFIX)) {
                                return e;
                            }
                        }

                        // return null if we're at the end
                        return null;
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    /**
     * Computes the SQL data type for a class name using {@link #SQL_TYPE_MAP}.
     */
    private String getSQLType(String className) {
        String sqlType = SQL_TYPE_MAP.get(className);
        if (sqlType == null) {
            // TODO implemented back-up strategy
            throw new IllegalArgumentException("Type not supported: " + className);
        }

        return sqlType;
    }

    /**
     * Registers the observer in the dim_obsever table (if not already in there).
     *
     * @return The identifier of the observer id in dim_observer.
     */
    private long registerObserver(Connection con) throws SQLException {
        PreparedStatement insert = con.prepareStatement("INSERT INTO `dim_observer` (`observedBy`, `observationOf`, `type`) VALUES (?,?,?);",
                                                        Statement.RETURN_GENERATED_KEYS);

        try {
            insert.setString(1, getObservedBy());
            insert.setString(2, getObservationOf());
            insert.setString(3, getObservationType());

            int updateCount = insert.executeUpdate();
            assert updateCount == 1;

            ResultSet generatedKeys = insert.getGeneratedKeys();
            boolean results = generatedKeys.next();
            assert results;

            return generatedKeys.getLong(1);
        } finally {
            insert.close();
        }
    }

    /**
     * Lookup the observer id (if not already known).
     */
    private Long lookupObserverId(Connection con) throws SQLException {
        // if already computed, return cached result
        // not thread safe, but doesn't need to be and is faster
        if (observerId != null) {
            return observerId;
        }

        PreparedStatement select = con.prepareStatement("SELECT id FROM `dim_observer` WHERE `observedBy` = ? AND `observationOf` = ?");

        try {
            select.setString(1, getObservedBy());
            select.setString(2, getObservationOf());

            ResultSet observerIds = select.executeQuery();
            if (observerIds.next() == false) {
                // return null if there isn't an observer registered
                // with the given 'observed by' and 'observation of'
                return null;
            }

            // return the observer id
            return observerId = observerIds.getLong(1);
        } finally {
            select.close();
        }
    }

    private String getObservationOf() {
        return (String) providerProperties.get(ObservationProviderRegistrationHelper.KEY_OBSERVATION_OF);
    }

    private String getObservedBy() {
        return (String) providerProperties.get(ObservationProviderRegistrationHelper.KEY_OBSERVED_BY);
    }

    private String getObservationType() {
        String[] type = (String[]) providerProperties.get(ObservationProviderRegistrationHelper.KEY_OBSERVATION_TYPE);
        return type[0];
    }

    /**
     * Creates a name for the fact table
     */
    private String getFactTableName() {
        // if already computed, return cached result
        // not thread safe, but doesn't need to be and is faster
        if (factTableName != null) {
            return factTableName;
        }

        String name = getObservationType();
        name = name.replaceAll("(\\w)\\w*[\\.$]", "$1.");
        name = name.toLowerCase();
        return FACT_PREFIX + name;
    }

    /**
     * Consumes an observation and writes it to the database.
     *
     * @see org.flexiblepower.observation.ObservationConsumer#consume(org.flexiblepower
     *      .observation.ObservationProvider, org.flexiblepower.observation.Observation)
     */
    @Override
    public void consume(ObservationProvider source, Observation observation) {
        // silently ignore null observations
        if (observation == null) {
            return;
        }

        try {
            queue.put(observation);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    insert();
                }
            });
        } catch (InterruptedException e) {
            logger.warn("Ignoring observation because of interrupt", e);
        }
    }

    private void insert() {
        // if the queue is empty, return
        if (queue.size() == 0) {
            return;
        }

        // drain all current observations to a list
        List<Observation> observations = new ArrayList<Observation>(queue.size());
        int count = queue.drainTo(observations);

        // if the queue is empty (may be cleared by another thread ...), return
        if (count == 0) {
            return;
        }

        try {
            Connection con = dataSource.getConnection();

            try {
                // TODO write in small batches?
                // TODO re-use the prepared statement?
                String sql = getInsertQuery();
                PreparedStatement insert = con.prepareStatement(sql);

                try {
                    // and batch up insert statements
                    for (Observation observation : observations) {
                        // the index to track which field in statement is set
                        int idx = 1;

                        // set the observer identifier
                        insert.setLong(idx++, lookupObserverId(con));

                        // set the time stamp, date and time fields
                        long observedAt = observation.getObservedAt().getTime();
                        insert.setTimestamp(idx++, new java.sql.Timestamp(observedAt));
                        insert.setDate(idx++, new java.sql.Date(observedAt));
                        insert.setTime(idx++, new java.sql.Time(observedAt / 1000 / 60 * 1000 * 60));

                        // for each field in the observer meta-data add the value
                        // from the observation or null if it didn't exist
                        Map<String, Object> values = observation.getValueMap();
                        for (String fieldName : getFieldNames()) {
                            Object value = values.get(fieldName);
                            if (value instanceof Measure) {
                                Measure measure = (Measure) value;
                                insert.setObject(idx++, measure.doubleValue(measure.getUnit()));
                            } else {
                                insert.setObject(idx++, value);
                            }
                        }

                        // add to batch
                        insert.addBatch();
                    }

                    // execute the insert batch
                    int updated = insert.executeUpdate();
                    assert updated == 1;
                } finally {
                    insert.close();
                }
            } finally {
                con.close();
            }
        } catch (Exception e) {
            logger.error("Couldn't write an obsevation to the database", e);
        }
    }

    /**
     * @return The names of the fields as per the meta-data of the observation provider ({@link #providerProperties}).
     */
    private List<String> getFieldNames() {
        // if already computed, return cached result
        // not thread safe, but doesn't need to be and is faster
        if (fieldNames != null) {
            return fieldNames;
        }

        // keep all fields in a list (the order is important ...)
        List<String> fieldNames = new ArrayList<String>();
        for (Entry<String, Object> field : getObservationTypeFields()) {
            fieldNames.add(field.getKey());
        }

        Collections.sort(fieldNames);

        return this.fieldNames = fieldNames;
    }

    /**
     * @return The query to be used as prepared statement for insertion of observation values in to the fact table for
     *         this observation provider.
     */
    private String getInsertQuery() {
        // if already computed, return cached result
        // not thread safe, but doesn't need to be and is faster
        if (insertQuery != null) {
            return insertQuery;
        }

        // specify the target table
        StringBuilder insert = new StringBuilder("INSERT INTO `").append(getFactTableName()).append("` (");

        // add default fields
        insert.append("`observerId`,");
        insert.append("`timestamp`,");
        insert.append("`dateId`,");
        insert.append("`timeId`,");

        // add all field names
        for (String field : getFieldNames()) {
            insert.append(" `");
            insert.append(field);
            insert.append("`,");
        }

        // strip last comma
        insert.setLength(insert.length() - 1);

        // end the fields part and open the values part
        insert.append(") VALUES (");

        // add place holders for the default fields
        insert.append("?,");
        insert.append("?,");
        insert.append("?,");
        insert.append("?,");

        // add place holders for the observation provider specific fields
        for (int i = 0; i < fieldNames.size(); i++) {
            insert.append("?,");
        }

        // strip last comma
        insert.setLength(insert.length() - 1);

        // and close the query
        insert.append(")");

        return insertQuery = insert.toString();
    }
}
