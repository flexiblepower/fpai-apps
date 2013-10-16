package nl.tno.fpai.monitoring.db.pooleddatasource;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.PooledConnection;

import org.osgi.service.jdbc.DataSourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.ConfigurationPolicy;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta.AD;

/**
 * OSGi component which provides pooled connections to a SQL database through JDBC. For this it requires a
 * DataSourceFactory.
 */
// TODO use a library for this ... couldn't find one quickly enough :s
@Component(configurationPolicy = ConfigurationPolicy.require, designate = PooledDataSource.Config.class)
public class PooledDataSource implements DataSource, ConnectionEventListener {
    /** the maximum size of the pool (all connections, not just the ones available) */
    private static final int POOL_SIZE = 16;

    public interface Config {
        @AD(deflt = "jdbc:mysql://localhost:3306/fpai_monitoring")
        String jdbcURL();

        @AD(deflt = "fpai")
        String jdbcUser();

        @AD(deflt = "fpai")
        String jdbcPassword();
    }

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    /** configuration with connection details (url, username and password) */
    private Config configuration;

    /** the service which provides the actual data source */
    private DataSourceFactory dataSourceFactory;
    /** the data source which provides the pooled connections used in this pool */
    private ConnectionPoolDataSource connectionPoolDataSource;

    /** the count of currently active connections (either in available or given to a client) */
    private AtomicInteger active = new AtomicInteger();
    /** the pool of connections available for clients to take */
    private BlockingQueue<PooledConnection> available = new LinkedBlockingDeque<PooledConnection>(POOL_SIZE);

    /**
     * Configures and activates the PooleDataSource
     * 
     * @param properties
     *            The configuration for this component with jdbc.URL, jdbc.User and jdbc.Password as required fields
     */
    @Activate
    public void activate(Map<String, Object> properties) {
        this.logger.info("Creating DataSource with connection pool with properties {}", properties);

        this.configuration = Configurable.createConfigurable(Config.class, properties);

        try {
            // create a first connection (to 'heat' the pool and to detect
            // connection errors 'somewhat' earlier then on first use)
            this.getConnection();
        } catch (SQLException e) {
            this.logger.warn("Couldn't initialize a first connection for the connection pool", e);
        }
    }

    /**
     * Configures the data source factory to use in the pool.
     * 
     * @param dataSourceFactory
     *            The factory used to obtain a (connection pool) data source from.
     */
    @Reference
    public void setDataSourceFactory(DataSourceFactory dataSourceFactory) {
        this.dataSourceFactory = dataSourceFactory;
    }

    @Override
    public Connection getConnection() throws SQLException {
        // try to take an available connection from the pool
        PooledConnection con = this.available.poll();
        if (con != null) {
            // and return it if it's there
            return con.getConnection();
        }

        // otherwise we have to create a new connection
        // or wait for one to become available if POOL_SIZE is reached
        while (true) {
            int active = this.active.get();

            // if at max, wait for connection to become available
            if (active >= POOL_SIZE) {
                try {
                    con = this.available.take();
                    return con.getConnection();
                } catch (InterruptedException e) {
                    // signal any calling code that this thread got
                    // interrupted
                    Thread.currentThread().interrupt();
                    // and try to return a connection again
                    continue;
                }
            }

            // otherwise create a new connection
            else {
                // but there might be competition, so we loop and try again
                // if another thread just created a connection
                if (this.active.compareAndSet(active, active + 1) == false) {
                    continue;
                }

                assert this.active.get() <= POOL_SIZE;

                final PooledConnection newcon = this.getConnectionPool().getPooledConnection();
                newcon.addConnectionEventListener(this);
                return newcon.getConnection();
            }
        }
    }

    /**
     * Called when a pooled connection is closed. It is returned to the available connections pool.
     * 
     * @see javax.sql.ConnectionEventListener#connectionClosed(javax.sql.ConnectionEvent)
     */
    @Override
    public void connectionClosed(ConnectionEvent event) {
        PooledConnection con = (PooledConnection) event.getSource();

        try {
            this.available.put(con);
        } catch (InterruptedException e) {
            try {
                con.close();
            } catch (SQLException e1) {
                // ignore
            } finally {
                this.active.decrementAndGet();
                assert this.active.get() <= POOL_SIZE;
            }
        }
    }

    /**
     * Called when a connection level error occurs. We close the connection and decrease the active count to make room
     * for a new connection.
     * 
     * @see javax.sql.ConnectionEventListener#connectionErrorOccurred(javax.sql.ConnectionEvent)
     */
    @Override
    public void connectionErrorOccurred(ConnectionEvent event) {
        try {
            PooledConnection con = (PooledConnection) event.getSource();
            con.close();
        } catch (SQLException e) {
            // ignore
        } finally {
            this.active.decrementAndGet();
            assert this.active.get() <= POOL_SIZE;
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return this.getConnectionPool().getPooledConnection(username, password).getConnection();
    }

    /**
     * Returns (and creates if called for the first time) a connection pool data source to use for creating poolable
     * connections.
     * 
     * @return The data source with poolable connections.
     * @throws SQLException
     *             when a connection can't be created.
     */
    private ConnectionPoolDataSource getConnectionPool() throws SQLException {
        if (this.connectionPoolDataSource != null) {
            return this.connectionPoolDataSource;
        }

        synchronized (this.dataSourceFactory) {
            if (this.connectionPoolDataSource != null) {
                return this.connectionPoolDataSource;
            }

            Properties props = new Properties();
            props.put(DataSourceFactory.JDBC_URL, this.configuration.jdbcURL());
            props.put(DataSourceFactory.JDBC_USER, this.configuration.jdbcUser());
            props.put(DataSourceFactory.JDBC_PASSWORD, this.configuration.jdbcPassword());

            try {
                return this.connectionPoolDataSource = this.dataSourceFactory.createConnectionPoolDataSource(props);
            } catch (SQLException e) {
                this.logger.error("Couldn't retrieve ConnectionPoolDataSource from the data source factory service", e);
                throw e;
            }
        }
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return this.getConnectionPool().getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        this.getConnectionPool().setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        this.getConnectionPool().setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return this.getConnectionPool().getLoginTimeout();
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        try {
            return this.getConnectionPool().getParentLogger();
        } catch (SQLException e) {
            throw new SQLFeatureNotSupportedException(e);
        }
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException();
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }
}
