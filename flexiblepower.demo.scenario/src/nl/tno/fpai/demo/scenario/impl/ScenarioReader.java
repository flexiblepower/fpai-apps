package nl.tno.fpai.demo.scenario.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import nl.tno.fpai.demo.scenario.data.Connection;
import nl.tno.fpai.demo.scenario.data.IdSet;
import nl.tno.fpai.demo.scenario.data.Scenario;
import nl.tno.fpai.demo.scenario.data.ScenarioConfiguration;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class ScenarioReader extends DefaultHandler {
    public static Map<String, Scenario> readScenarios(Reader reader) throws IOException {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser parser = spf.newSAXParser();
            XMLReader xmlReader = parser.getXMLReader();
            ScenarioReader handler = new ScenarioReader();
            xmlReader.setContentHandler(handler);
            xmlReader.parse(new InputSource(reader));
            return handler.getResult();
        } catch (SAXException ex) {
            throw new IOException(ex);
        } catch (ParserConfigurationException ex) {
            throw new IOException(ex);
        }
    }

    private Map<String, Scenario> result = null;
    private Scenario.Builder currentScenario = null;
    private ScenarioConfiguration.Builder currentConfig = null;
    private String configKey = null;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (result == null) {
            expect("scenarios", qName);
            result = new TreeMap<String, Scenario>();
        } else if (currentScenario == null) {
            expect("scenario", qName);
            currentScenario = new Scenario.Builder();
            String name = expect(attributes, "name");
            if (result.containsKey(name)) {
                throw error("Duplicate scenario with name [" + name + "]");
            }
            currentScenario.setName(name);
        } else if (currentConfig == null) {
            if ("idSet".equals(qName)) {
                String name = expect(attributes, "name");
                int count = optional(attributes, "count", 1);
                IdSet idSet = new IdSet(name, count);
                currentScenario.addIdSet(idSet);
            } else if ("config".equals(qName)) {
                String bundleId = expect(attributes, "bundleId");
                String serviceId = attributes.getValue("serviceId");
                String factoryId = attributes.getValue("factoryId");
                String reference = attributes.getValue("ref");
                if ((serviceId == null) == (factoryId == null)) {
                    throw error("We need either a serviceId or a factoryId for a config element");
                }
                String idRef = optional(attributes, "idRef", null);
                String type = optional(attributes, "type", "multiple");

                currentConfig = new ScenarioConfiguration.Builder();
                currentConfig.setBundleId(bundleId);
                if (serviceId != null) {
                    currentConfig.setServiceId(serviceId);
                } else if (factoryId != null) {
                    currentConfig.setFactoryId(factoryId);
                }
                if (reference != null) {
                    currentConfig.setReference(reference);
                }
                currentConfig.setIdRef(idRef);
                currentConfig.setType(type);
            } else if ("connection".equals(qName)) {
                String to = expect(attributes, "to");
                String from = expect(attributes, "from");

                String[] toParts = to.split(":");
                String[] fromParts = from.split(":");
                if (toParts.length != 2 || fromParts.length != 2) {
                    throw error("Illegal format for a connection element");
                }

                Connection connection = new Connection(fromParts[0], toParts[0], fromParts[1], toParts[1]);
                currentScenario.addConnection(connection);
            }
        } else if (configKey == null) {
            configKey = qName;
            currentConfig.setProperty(configKey, "");
        } else {
            throw error("Unexpected element with name [" + qName + "]");
        }
    }

    private void expect(String expectedName, String name) throws SAXException {
        if (!expectedName.equals(name)) {
            throw error("Expected element with name [" + expectedName + "], found [" + name + "]");
        }
    }

    private String expect(Attributes attributes, String name) throws SAXException {
        String value = attributes.getValue(name);
        if (value == null) {
            throw error("Expected attribute with name [" + name + "]");
        }
        return value;
    }

    private String optional(Attributes attributes, String name, String deflt) {
        String value = attributes.getValue(name);
        if (value == null) {
            return deflt;
        } else {
            return value;
        }
    }

    private int optional(Attributes attributes, String name, int deflt) throws SAXException {
        String value = attributes.getValue(name);
        if (value == null) {
            return deflt;
        } else {
            try {
                return Integer.valueOf(value);
            } catch (NumberFormatException ex) {
                throw error(ex);
            }
        }
    }

    private SAXException error(Exception ex) {
        return new SAXException(ex.getMessage() + toString(), ex);
    }

    private SAXException error(String msg) {
        return new SAXException(msg + toString());
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (currentConfig != null && configKey != null) {
            currentConfig.setProperty(configKey, new String(ch, start, length).trim());
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (configKey != null) {
            expect(configKey, qName);
            configKey = null;
        } else if (currentConfig != null) {
            expect("config", qName);
            currentScenario.addConfiguration(currentConfig.build());
            currentConfig = null;
        } else if ("idSet".equals(qName) || "connection".equals(qName)) {
            // OK, nothing to reset
        } else if ("scenario".equals(qName)) {
            Scenario scenario = currentScenario.build();
            result.put(scenario.getName(), scenario);
            currentScenario = null;
        } else {
            expect("scenarios", qName);
        }
    }

    public Map<String, Scenario> getResult() {
        return result;
    }

    @Override
    public String toString() {
        if (result == null) {
            return "(Current state: Not started)";
        } else if (currentScenario == null) {
            return "(Current state: Just started, no scenario)";
        } else if (currentConfig == null) {
            return ("Current state: parsing scenario [" + currentScenario.build().getName() + "]");
        } else {
            return ("Current state: parsing scenario [" + currentScenario.build().getName()
                    + "] -> config ["
                    + currentConfig.build().getId() + "]");
        }
    }
}
