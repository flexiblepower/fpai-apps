package org.flexiblepower.monitoring.ui.web.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLNonTransientException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;

/**
 * OSGi Component which registers a {@link javax.servlet.Servlet} as a service in the OSGi service registry. The service
 * is capable of performing queries (given in the 'q' parameter in a request) on the DataSource which is used by this
 * component.
 */
@Component(properties = { "alias=/ui/sql" }, immediate = true)
public class SQLServlet extends HttpServlet implements Servlet {
    private static final long serialVersionUID = 1L;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Map<String, DataSource> dataSources = new HashMap<String, DataSource>();

    /**
     * @param dataSource
     *            The DataSource to query.
     */
    @Reference(multiple = true, dynamic = true, optional = false)
    public void addDataSource(DataSource dataSource) {
        try {
            DatabaseMetaData metaData = dataSource.getConnection().getMetaData();
            String identifier = metaData.getURL(); // metaData.getUserName();
            dataSources.put(identifier, dataSource);
        } catch (SQLException e) {
            logger.warn("A data source was bound to this SQLServlet, but retreiving meta-data caused an error. Ignoring the data source: " + dataSource,
                        e);
        }
    }

    /**
     * @param dataSource
     *            The DataSource to remove.
     */
    public void removeDataSource(DataSource dataSource) {
        try {
            DatabaseMetaData metaData = dataSource.getConnection().getMetaData();
            String identifier = metaData.getURL(); // metaData.getUserName();
            dataSources.remove(identifier);
        } catch (SQLException e) {
            logger.warn("A data source was unbound to this SQLServlet, but retreiving meta-data caused an error. Ignoring removal of data source: " + dataSource,
                        e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain");

        String cmd = req.getParameter("cmd");
        if ("list-data-sources".equals(cmd)) {
            doListDataSources(req, resp);
        } else if ("query".equals(cmd)) {
            doQuery(req, resp);
        } else {
            PrintWriter out = resp.getWriter();
            out.print("Command '");
            out.print(cmd);
            out.print("' is not supported.");
        }
    }

    private void doListDataSources(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PrintWriter out = resp.getWriter();

        out.println("data-source-identifier");
        for (String dsId : dataSources.keySet().toArray(new String[dataSources.size()])) {
            out.print(dsId);
            out.print("\n");
        }
    }

    private void doQuery(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PrintWriter out = resp.getWriter();

        String source = req.getParameter("ds");
        String query = req.getParameter("q");

        DataSource dataSource = dataSources.get(source);
        if (dataSource == null) {
            out.print("Unkown data source with identifier: ");
            out.print(source);
            out.print("\n");
        }

        Connection con = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            con = dataSource.getConnection();
            stmt = con.createStatement();
            rs = stmt.executeQuery(query);

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            for (int i = 1; i <= columnCount; i++) {
                out.print(meta.getColumnName(i));

                if (i < columnCount) {
                    out.print("\t");
                }
            }
            out.print("\n");

            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    out.print(rs.getString(i));

                    if (i < columnCount) {
                        out.print("\t");
                    }
                }
                out.print("\n");
            }
        } catch (SQLException e) {
            String msg = "An error occurred: \"" + e.getMessage().replaceAll("\r?\n", " ")
                         + "\" while executing: "
                         + query.replaceAll("\r?\n", " ").trim();
            logger.error(msg, e);

            if (e instanceof SQLNonTransientException) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } else { // if(e instance of SQLTransientException){
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }

            out.print(msg);
        } finally {
            close(rs);
            close(stmt);
            close(con);
        }
    }

    private void close(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (Exception e) {
                logger.warn("Failed to close result set", e);
            }
        }
    }

    private void close(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (Exception e) {
                logger.warn("Failed to close statement", e);
            }
        }
    }

    private void close(Connection con) {
        if (con != null) {
            try {
                con.close();
            } catch (Exception e) {
                logger.warn("Failed to close connection", e);
            }
        }
    }
}
