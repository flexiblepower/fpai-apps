package org.flexiblepower.monitoring.mysql.writer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.Duration;
import javax.measure.unit.Unit;

import org.flexiblepower.context.FlexiblePowerContext;
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
    private final class InitializeTable implements Runnable {
        @Override
        public void run() {
            try {
                synchronized (ObservationWriter.this) {
                    Connection con = dataSource.createConnection();

                    if (con == null) {
                        // Can not start at this time, try again later?
                    }

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
                    provider.subscribe(ObservationWriter.this);
                }
            } catch (SQLException ex) {
                logger.error("Couldn't activate an observation writer", ex);
                close();
            }
        }

        @Override
        public String toString() {
            return "Initializing the MySQL table for this observation type";
        }
    }

    private final class DoInsert implements Runnable {
        @Override
        public void run() {
            insert();
        }

        @Override
        public String toString() {
            return "Writing observations into the MySQL database";
        }
    }

    /** The maximum amount of observations to queue */
    private static final int QUEUE_SIZE = 64;

    /** The prefix of fact tables used in the database. */
    private static final String FACT_PREFIX = "fact_";

    /** A map of of java classes to the SQL types to use. */
    @SuppressWarnings("serial")
    private static final Map<String, String> SQL_TYPE_MAP = Collections.unmodifiableMap(new HashMap<String, String>() {
        {
            // numbers
            put("boolean", "BIT");
            put("int", "INTEGER");
            put("long", "BIGINT");
            put("float", "FLOAT");
            put("double", "DOUBLE");
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
            put("string", "TEXT");

            // time and date
            put(java.util.Date.class.getName(), "TIMESTAMP");
            put(java.sql.Timestamp.class.getName(), "TIMESTAMP");
            put(java.sql.Time.class.getName(), "TIME");
            put(java.sql.Date.class.getName(), "DATE");
        }
    });

    /** The default SQL type to use */
    private static final String DEFAULT_SQL_TYPE = "TEXT";

    /** The index of the column with table names from {@link DatabaseMetaData#getTables}. */
    private static final int TABLE_NAME_COLUMN_IDX = 3;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /** The data source to use for getting connections to the database. */
    private final ObservationWriterManager dataSource;

    /** The provider of observations to write to the database. */
    private final ObservationProvider provider;
    /** The meta-data properties of the provider. */
    private final Map<String, Object> providerProperties;

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
    private final FlexiblePowerContext context;

    private ScheduledFuture<?> schedule;

    public ObservationWriter(FlexiblePowerContext context,
                             ObservationWriterManager observationWriterManager,
                             ObservationProvider provider,
                             Map<String, Object> properties) {
        this.context = context;
        dataSource = observationWriterManager;
        this.provider = provider;
        providerProperties = properties;
    }

    public void start(Measurable<Duration> updateRate) {
        if (schedule != null) {
            schedule.cancel(false);
        }
        context.submit(new InitializeTable());
        schedule = context.scheduleAtFixedRate(new DoInsert(), updateRate, updateRate);
    }

    /**
     * Closes this writer
     */
    public void close() {
        if (provider != null) {
            provider.unsubscribe(this);
        }
        if (schedule != null) {
            schedule.cancel(false);
        }
        // Run one last time to make sure the queue is empty
        insert();
    }

    /**
     * Checks if the fact table for storing the observations exists, and if not, creates the fact table.
     */
    private void ensureTableExists(Connection con) throws SQLException {
        if (!doesFactTableExist(con, getFactTableName())) {
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
            if (tables.getString(TABLE_NAME_COLUMN_IDX).equals(factTableName)) {
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
        create.append("  `observedAt` timestamp(3) NOT NULL DEFAULT 0,\n");
        create.append("  `dateId` date NOT NULL,\n");
        create.append("  `timeId` time NOT NULL,\n");

        // and the fields specific to the observation provider
        for (String fieldName : getFieldNames()) {
            create.append("  `");
            create.append(getSQLColumnName(fieldName));
            create.append("` ");
            create.append(getSQLType((String) providerProperties.get(ObservationProviderFieldsFilter.FIELD_PREFIX + fieldName)));
            create.append(",\n");
        }

        // set the primary key
        create.append("  PRIMARY KEY (`observerId`, `observedAt`),\n");

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
     * Provides an iterator over the fields in the meta-data of the observation provider which indicate a field.
     */
    private Iterable<Entry<String, Object>> getObservationTypeFields() {
        return new ObservationProviderFieldsFilter(providerProperties);
    }

    /**
     * Computes the SQL data type for a class name using {@link #SQL_TYPE_MAP} or {@link #DEFAULT_SQL_TYPE} if no
     * specific type is available.
     */
    private String getSQLType(String className) {
        String sqlType = SQL_TYPE_MAP.get(className);
        if (sqlType == null) {
            return DEFAULT_SQL_TYPE;
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
            int idx = 1;
            insert.setString(idx++, getObservedBy());
            insert.setString(idx++, getObservationOf());
            insert.setString(idx++, Arrays.toString(getObservationTypes()));

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
            if (!observerIds.next()) {
                // return null if there isn't an observer registered
                // with the given 'observed by' and 'observation of'
                return null;
            }

            // return the observer id
            observerId = observerIds.getLong(1);
            return observerId;
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

    private String[] getObservationTypes() {
        Object type = providerProperties.get("org.flexiblepower.monitoring.type");

        if (type instanceof String[]) {
            return (String[]) type;
        } else if (type instanceof String) {
            return new String[] { (String) type };
        } else {
            throw new IllegalArgumentException("The observation type: " + type + " is not supported.");
        }
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

        StringBuilder name = new StringBuilder(FACT_PREFIX);

        String[] types = getObservationTypes();
        for (int i = 0; i < types.length; i++) {
            String type = types[i];

            int lastDotIdx = Math.max(type.lastIndexOf('.'), type.lastIndexOf('$'));

            // prefix with fact_, concatenate first letters of all packages, add _ and class name (in lower case)
            // name.append(type.substring(0, lastDotIdx + 1).replaceAll("(\\w)\\w*[\\.\\$$]", "$1").toLowerCase());
            // name.append("_");
            name.append(type.substring(lastDotIdx + 1).toLowerCase());

            if (i < types.length - 1) {
                name.append("_");
            }
        }

        return name.toString();
    }

    /** @return The field name transformed into a SQL compatible column name. */
    private String getSQLColumnName(String fieldName) {
        return fieldName.replace('.', '_');
    }

    /*
     * Consumes an observation and writes it to the database.
     * 
     * @see org.flexiblepower.observation.ObservationConsumer#consume(org.flexiblepower
     * .observation.ObservationProvider, org.flexiblepower.observation.Observation)
     */
    @Override
    public void consume(ObservationProvider source, Observation observation) {
        // silently ignore null observations
        if (observation == null) {
            return;
        }

        if (queue.offer(observation)) {
            // Queue not full
        } else {
            // Queue full
            logger.warn("MySQL writers observation queue is full, observation not saved");
        }

        if (queue.size() > QUEUE_SIZE - 5) {
            context.submit(new DoInsert());
        }
    }

    synchronized void insert() {
        // if the queue is empty, return
        if (queue.size() == 0) {
            return;
        }

        // drain all current observations to a list
        List<Observation> observations = new ArrayList<Observation>(QUEUE_SIZE);
        int count = queue.drainTo(observations);

        // if the queue is empty (may be cleared by another thread ...), return
        if (count == 0) {
            return;
        }

        try {
            Connection con = dataSource.createConnection();
            logger.debug("Writing {} observations to MySQL", count);
            try {
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
                        long observedAtMillis = observation.getObservedAt().getTime();
                        long observedAtMinutes = TimeUnit.MINUTES.toMillis(TimeUnit.MILLISECONDS.toMinutes(observedAtMillis));
                        insert.setTimestamp(idx++, new java.sql.Timestamp(observedAtMillis));
                        insert.setDate(idx++, new java.sql.Date(observedAtMillis));
                        insert.setTime(idx++, new java.sql.Time(observedAtMinutes));

                        // for each field in the observer meta-data add the value
                        // from the observation or null if it didn't exist
                        Map<String, Object> values = observation.getValueMap();
                        for (String fieldName : getFieldNames()) {
                            insertValue(insert, idx++, values.get(fieldName));
                        }

                        // add to batch
                        insert.addBatch();
                    }

                    // execute the insert batch
                    insert.executeBatch();
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

    private void insertValue(PreparedStatement insert, int idx, Object value) throws SQLException {
        if (value != null) {
            // process measures into doubles
            if (value instanceof Measure) {
                Measure measure = (Measure) value;
                Unit unit = measure.getUnit();
                // TODO consider: unit = unit.getStandardUnit();
                value = measure.doubleValue(unit);
            } else if (value instanceof Measurable && ((Measurable) value).doubleValue(Dimensionless.UNIT) == 0.0) {
                value = 0.0d;
            }

            // process NaN's into null
            if (value instanceof Float && ((Float) value).isNaN()) {
                value = null;
            } else if (value instanceof Double && ((Double) value).isNaN()) {
                value = null;
            }
        }

        // let JDBC convert if supported type
        // if not supported, insert as string
        if (value == null) {
            insert.setObject(idx, null);
        } else if (SQL_TYPE_MAP.containsKey(value.getClass().getName())) {
            insert.setObject(idx, value);
        } else if (value.getClass().isArray()) {
            insert.setString(idx, Arrays.toString((Object[]) value));
        } else {
            insert.setString(idx, String.valueOf(value));
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
        List<String> names = new ArrayList<String>();
        for (Entry<String, Object> field : getObservationTypeFields()) {
            String fieldName = field.getKey();

            if ("observerId".equals(fieldName) || "observedAt".equals(fieldName)
                || "dateId".equals(fieldName)
                || "timeId".equals(fieldName)) {
                logger.warn("Ignoring field with name {}, it's a reserved name", fieldName);
            } else {
                names.add(fieldName);
            }
        }

        Collections.sort(names);

        fieldNames = names;
        return fieldNames;
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
        insert.append("`observedAt`,");
        insert.append("`dateId`,");
        insert.append("`timeId`,");

        // add all field names
        for (String fieldName : getFieldNames()) {
            insert.append(" `");
            insert.append(getSQLColumnName(fieldName));
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

        insertQuery = insert.toString();
        return insertQuery;
    }
}
