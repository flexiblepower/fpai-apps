package org.flexiblepower.monitoring.mysql.pooled;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.ConfigurationPolicy;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta.AD;

import com.mysql.jdbc.Driver;

/**
 * OSGi component which provides connections to a SQL database through JDBC. For this it requires a DataSourceFactory.
 */
// TODO use a library for this ... couldn't find one quickly enough :s
@Component(configurationPolicy = ConfigurationPolicy.require, designate = MySQLDataSource.Config.class)
public class MySQLDataSource implements DataSource {
    /** the maximum size of the pool (all connections, not just the ones available) */

    public interface Config {
        @AD(deflt = "jdbc:mysql://fpaimonitoring.sensorlab.tno.nl:3306/vios_test")
        String jdbcURL();

        @AD(deflt = "fpai")
        String jdbcUser();

        @AD(deflt = "fpai")
        String jdbcPassword();
    }

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /** configuration with connection details (url, username and password) */
    private Config configuration;

    /**
     * Configures and activates the PooleDataSource
     *
     * @param properties
     *            The configuration for this component with jdbc.URL, jdbc.User and jdbc.Password as required fields
     */
    @Activate
    public void activate(Map<String, Object> properties) {
        logger.info("Creating DataSource with connection pool with properties {}", properties);

        configuration = Configurable.createConfigurable(Config.class, properties);
    }

    @Override
    public Connection getConnection() throws SQLException {
        new Driver();
        Connection conn = DriverManager.getConnection(configuration.jdbcURL(),
                                                      configuration.jdbcUser(),
                                                      configuration.jdbcPassword());
        return conn;
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        // TODO Auto-generated method stub

    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean isWrapperFor(Class<?> arg0) throws SQLException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> arg0) throws SQLException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        // TODO Auto-generated method stub
        return null;
    }

}
