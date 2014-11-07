package nl.tno.fpai.demo.scenario.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;

@Component(provide = Servlet.class, immediate = true, properties = "alias=/currentConfig.html")
public class CurrentConfigurationServlet extends HttpServlet {
    private static final long serialVersionUID = -8933643059131786150L;
    private static final Logger logger = LoggerFactory.getLogger(CurrentConfigurationServlet.class);
    private static final Set<String> blacklist = new HashSet<String>(Arrays.asList("service.pid",
                                                                                   "service.factoryPid",
                                                                                   "component.id",
            "component.name"));

    private ConfigurationAdmin configAdmin;

    @Reference
    public void setConfigAdmin(ConfigurationAdmin configAdmin) {
        this.configAdmin = configAdmin;
    }

    private BundleContext context;

    @Activate
    public void activate(BundleContext context) {
        this.context = context;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            PrintWriter w = resp.getWriter();
            w.println("<html>");
            w.println("<head><style type=\"text/css\">p { font-family: \"Lucida Console\", monospace; font-size: 9pt; margin: 0.4em; }</style></head>");
            w.println("<body>");
            Configuration[] configurations = configAdmin.listConfigurations(null);
            if (configurations != null) {
                for (Configuration configuration : configurations) {
                    if ("org.flexiblepower.runtime.messaging.ConnectionManagerImpl".equals(configuration.getPid())) {
                        continue; // Skip the connections
                    }

                    w.print("<p>&lt;config");
                    String bundleLocation = configuration.getBundleLocation();
                    if (bundleLocation != null) {
                        Bundle bundle = context.getBundle(configuration.getBundleLocation());
                        if (bundle != null) {
                            w.print(" bundleId=\"");
                            w.print(bundle.getSymbolicName());
                            w.print("\"");
                        }
                    }
                    if (configuration.getFactoryPid() != null) {
                        w.print(" factoryId=\"");
                        w.print(configuration.getFactoryPid());
                        w.print("\"");
                    } else {
                        w.print(" serviceId=\"");
                        w.print(configuration.getPid());
                        w.print("\"");
                    }
                    w.println("&gt;</p>");

                    Dictionary<String, Object> properties = configuration.getProperties();
                    Enumeration<String> keys = properties.keys();
                    while (keys.hasMoreElements()) {
                        String key = keys.nextElement();
                        if (!blacklist.contains(key)) {
                            Object value = properties.get(key);
                            w.print("<p>&nbsp;&nbsp;&nbsp;&nbsp;&lt;");
                            w.print(key);
                            w.print("&gt;");
                            w.print(value.toString());
                            w.print("&lt;/");
                            w.print(key);
                            w.print("&gt;</p>");
                        }
                    }

                    w.println("<p>&lt;/config&gt;</p><br />");
                }
            }
            w.println("</body>");
            w.println("</html>");
        } catch (Exception ex) {
            logger.warn("Error while generating scenario XML: " + ex.getMessage(), ex);
        }
    }
}
