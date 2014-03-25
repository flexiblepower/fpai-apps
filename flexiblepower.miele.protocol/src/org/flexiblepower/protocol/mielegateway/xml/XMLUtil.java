package org.flexiblepower.protocol.mielegateway.xml;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class XMLUtil {
    private static final Logger log = LoggerFactory.getLogger(XMLUtil.class);

    private static final XMLUtil singleton = new XMLUtil();

    public static XMLUtil get() {
        return singleton;
    }

    private final DocumentBuilderFactory factory;

    private XMLUtil() {
        factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setIgnoringComments(true);
        factory.setValidating(false);
        factory.setCoalescing(true);
        factory.setIgnoringElementContentWhitespace(true);
    }

    public Document parseXml(URL url) {
        InputStream is = null;
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            is = url.openStream();
            return builder.parse(is);
        } catch (IOException e) {
            log.warn("I/O error while reading XML", e);
        } catch (SAXException e) {
            log.warn("Parse error while reading XML", e);
        } catch (ParserConfigurationException e) {
            log.error("Configuration error while reading XML", e);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
            }
        }
        return null;
    }
}
