package org.flexiblepower.monitoring.elasticsearch;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;

@Component
public class Kibana {
    private static final String URL = "/kibana";
    // private static final String[] ES_URLS = new String[] { "/_nodes", "/_aliases" };

    private static final Logger logger = LoggerFactory.getLogger(Kibana.class);

    private final Proxy proxy = new Proxy();

    private HttpService httpService;

    @Reference
    public void setHttpService(HttpService httpService) {
        this.httpService = httpService;
    }

    @Activate
    public void activate() {
        try {
            httpService.registerResources(URL, "/kibana", null);
            // for (String esUrl : ES_URLS) {
            // httpService.registerServlet(esUrl, proxy, null, null);
            // }
            logger.info("Registered kibana dashboard at http://localhost:8080/kibana/index.html");
        } catch (NamespaceException e) {
            logger.warn(e.getMessage(), e);
            // } catch (ServletException e) {
            // logger.warn(e.getMessage(), e);
        }
    }

    @Deactivate
    public void deactivate() {
        httpService.unregister(URL);
        // for (String esUrl : ES_URLS) {
        // httpService.unregister(esUrl);
        // }
    }

    public static class Proxy extends HttpServlet {
        private static final long serialVersionUID = 5528747708243569598L;

        private final CloseableHttpClient httpClient = HttpClientBuilder.create().build();

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            String uri = "http://localhost:9200" + req.getRequestURI();
            if (req.getQueryString() != null) {
                uri += "?" + req.getQueryString();
            }

            HttpGet get = new HttpGet(uri);
            CloseableHttpResponse response = httpClient.execute(get);

            resp.setStatus(response.getStatusLine().getStatusCode());
            for (Header header : response.getAllHeaders()) {
                resp.setHeader(header.getName(), header.getValue());
            }
            IOUtils.copy(response.getEntity().getContent(), resp.getOutputStream());
            resp.flushBuffer();
        }

        public void close() {
            try {
                httpClient.close();
            } catch (IOException e) {
            }
        }
    }
}
