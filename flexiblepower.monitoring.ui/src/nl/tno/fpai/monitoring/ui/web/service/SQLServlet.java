package nl.tno.fpai.monitoring.ui.web.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;

@Component(properties = { "alias=/ui/sql" }, immediate = true)
public class SQLServlet extends HttpServlet implements Servlet {
	private static final long serialVersionUID = 1L;
	private DataSource dataSource;

	@Reference
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		resp.setContentType("text/plain");
		PrintWriter out = resp.getWriter();

		String sql = req.getParameter("q");
		// out.print("/*\n   results for query:\n      ");
		// out.print(sql.trim().replaceAll("\n", "\n      "));
		// out.print("\n*/\n");

		try {
			Connection con = dataSource.getConnection();
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
			} finally {
				close(rs);
				close(stmt);
				close(con);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
}
