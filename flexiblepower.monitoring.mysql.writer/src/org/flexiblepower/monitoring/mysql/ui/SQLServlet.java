package org.flexiblepower.monitoring.mysql.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLNonTransientException;
import java.sql.Statement;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.flexiblepower.monitoring.mysql.writer.ObservationWriterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OSGi Component which registers a {@link javax.servlet.Servlet} as a service in the OSGi service registry. The service
 * is capable of performing queries (given in the 'q' parameter in a request) on the DataSource which is used by this
 * component.
 */
public class SQLServlet extends HttpServlet implements Servlet {
    private static final long serialVersionUID = 5331255292113568774L;

    private static final Logger logger = LoggerFactory.getLogger(SQLServlet.class);

    private final ObservationWriterManager source;

    public SQLServlet(ObservationWriterManager source) {
        this.source = source;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain");

        String cmd = req.getParameter("cmd");
        if ("query".equals(cmd)) {
            doQuery(req, resp);
        } else {
            PrintWriter out = resp.getWriter();
            out.print("Command '");
            out.print(cmd);
            out.print("' is not supported.");
        }
    }

    private void doQuery(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PrintWriter out = resp.getWriter();

        String query = req.getParameter("q");

        Connection con = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            con = source.createConnection();
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
