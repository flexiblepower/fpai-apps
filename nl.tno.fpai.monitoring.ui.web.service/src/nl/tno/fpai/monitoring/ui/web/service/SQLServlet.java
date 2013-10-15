package nl.tno.fpai.monitoring.ui.web.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.jdbc.DataSourceFactory;

import aQute.bnd.annotation.component.Component;

@Component(properties = { "alias=/ui/sql" }, immediate = true)
public class SQLServlet extends HttpServlet implements Servlet {
	private static final long serialVersionUID = 1L;
	private DataSourceFactory dsf;
	private com.mysql.jdbc.Driver driver;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		resp.setContentType("text/plain");
		PrintWriter out = resp.getWriter();

		String sql = req.getParameter("q");

		Connection con = connect();
		Statement stmt = null;
		ResultSet rs = null;

		try {
			stmt = con.createStatement();
			rs = stmt.executeQuery(sql);

			ResultSetMetaData meta = rs.getMetaData();
			int columnCount = meta.getColumnCount();

			for (int i = 1; i <= columnCount; i++) {
				out.print(meta.getColumnName(i));
				out.print("\t");
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
			e.printStackTrace();
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
			}
		}
	}

	private void close(Statement stmt) {
		if (stmt != null) {
			try {
				stmt.close();
			} catch (Exception e) {
			}
		}
	}

	private void close(Connection con) {
		if (con != null) {
			try {
				con.close();
			} catch (Exception e) {
			}
		}
	}

	// private void close(Closeable closeable) {
	// if (closeable != null) {
	// try {
	// closeable.close();
	// } catch (Exception e) {
	// }
	// }
	// }

	// @Reference
	// public void setDataSourceFactory(DataSourceFactory dsf) {
	// this.dsf = dsf;
	// }

	private Connection connect() {
		try {
			// return this.dsf.createDataSource(new Properties() {
			// {
			// put("url", "jdbc:mysql://localhost:3306\ts");
			// put("user", "client");
			// put("password", "client");
			// }
			// }).getConnection();

			Class.forName("com.mysql.jdbc.Driver");
			Connection con = DriverManager.getConnection(
					"jdbc:mysql://localhost:3306/ts", "client", "client");
			return con;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
