package org.flexiblepower.miele.protocol.http;

/********************************************
 * Copyright (c) 2011 Alliander.            *
 * All rights reserved.                     *
 *                                          *
 * Contributors:                            *
 *     IBM - initial API and implementation *
 *     TNO - modifications for FPS          *
 *******************************************/

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author IBM, TNO
 * @version 0.7.0
 */
public final class HttpUtils {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);
    private static final int CONNECTION_TIME_OUT = 5000;
    private static final int READ_TIME_OUT = 3000;

    private HttpUtils() {
    }

    /**
     * @param urlString
     * @param encoding
     * @return TODO
     * @throws HttpUtilException
     */
    public static InputStream httpGet(final String urlString, final String encoding) throws HttpUtilException {

        try {
            logger.debug("HTTP GET on " + urlString);

            URL url = new URL(urlString);

            // Send data
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(CONNECTION_TIME_OUT);
            conn.setReadTimeout(READ_TIME_OUT);

            // Get the response
            return conn.getInputStream();

        } catch (Exception e) {
            String msg = "Error performing http get operation. " + e.getMessage();
            logger.error(msg);
            throw new HttpUtilException("Http get operation failed. ");
        }
    }

    /**
     * @param endpoint
     * @param requestParameters
     * @param encoding
     * @return TODO
     * @throws HttpUtilException
     */
    public static InputStream
            httpGet(final String endpoint, final String requestParameters, final String encoding) throws HttpUtilException {
        // Construct URL string
        String urlStr = endpoint;
        if (requestParameters != null && requestParameters.length() > 0) {
            urlStr += "?" + requestParameters;
        }

        // Get the response
        return httpGet(urlStr, encoding);
    }

}
