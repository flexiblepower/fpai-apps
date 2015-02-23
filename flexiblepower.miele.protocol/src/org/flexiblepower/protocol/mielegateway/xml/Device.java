package org.flexiblepower.protocol.mielegateway.xml;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Device {
    private static final Logger log = LoggerFactory.getLogger(Device.class);

    public static List<Device> parseDevices(Element element) {
        if (!"devices".equalsIgnoreCase(element.getNodeName())) {
            log.warn("The root element of the document is not <devices>");
            return Collections.emptyList();
        }

        List<Device> result = new ArrayList<Device>();

        NodeList nodes = element.getChildNodes();
        for (int ix = 0; ix < nodes.getLength(); ix++) {
            Node node = nodes.item(ix);
            if (node.getNodeType() == Node.ELEMENT_NODE && "device".equalsIgnoreCase(node.getNodeName())) {
                Device device = parseDevice((Element) node);
                if (device != null) {
                    result.add(device);
                }
            }
        }

        return result;
    }

    public static Device parseDevice(Element element) {
        if (!"device".equalsIgnoreCase(element.getNodeName())) {
            log.warn("The root element is not <device>");
            return null;
        }

        String name = null, type = null;
        Integer id = null;
        Map<String, String> information = null;
        Map<String, URL> actions = null;

        NodeList nodes = element.getChildNodes();
        for (int ix = 0; ix < nodes.getLength(); ix++) {
            Node node = nodes.item(ix);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                if (node.getNodeName().equals("name")) {
                    name = node.getTextContent();
                } else if (node.getNodeName().equals("UID")) {
                    id = Integer.parseInt(node.getTextContent());
                } else if (node.getNodeName().equals("type")) {
                    type = node.getTextContent();
                } else if (node.getNodeName().equals("information")) {
                    information = parseInformation((Element) node);
                } else if (node.getNodeName().equals("actions")) {
                    actions = parseActions((Element) node);
                }
            }
        }

        if (information == null || actions == null) {
            log.warn("Missing information or actions for the device with name [{}]", name);
            return null;
        } else {
            return new Device(name, id, type, information, actions);
        }
    }

    private static Map<String, URL> parseActions(Element element) {
        Map<String, URL> result = new HashMap<String, URL>();
        NodeList nodes = element.getChildNodes();
        for (int ix = 0; ix < nodes.getLength(); ix++) {
            Node node = nodes.item(ix);
            if (node.getNodeType() == Node.ELEMENT_NODE && "action".equals(node.getNodeName())) {
                NamedNodeMap attributes = node.getAttributes();
                Node name = attributes.getNamedItem("name");
                Node url = attributes.getNamedItem("URL");

                if (name != null && url != null) {
                    try {
                        result.put(name.getTextContent(), new URL(url.getTextContent()));
                    } catch (MalformedURLException e) {
                        // Ignore the action if the URL is not valid
                        log.warn("Illegal url in action [{}]: {}", name.getTextContent(), url.getTextContent());
                    }
                }
            }
        }
        return result;
    }

    private static Map<String, String> parseInformation(Element element) {
        Map<String, String> result = new HashMap<String, String>();
        NodeList nodes = element.getChildNodes();
        for (int ix = 0; ix < nodes.getLength(); ix++) {
            Node node = nodes.item(ix);
            if (node.getNodeType() == Node.ELEMENT_NODE && "key".equals(node.getNodeName())) {
                NamedNodeMap attributes = node.getAttributes();
                Node name = attributes.getNamedItem("name");
                Node value = attributes.getNamedItem("value");

                if (name != null && value != null) {
                    result.put(name.getTextContent(), value.getTextContent());
                }
            }
        }

        return result;
    }

    private final Integer id;
    private final String name, type;
    private final Map<String, String> information;
    private final Map<String, URL> actions;

    public Device(String name, Integer id, String type, Map<String, String> information, Map<String, URL> actions) {
        this.name = name;
        this.id = id;
        this.type = type;
        this.information = Collections.unmodifiableMap(information);
        this.actions = Collections.unmodifiableMap(actions);
    }

    public String getName() {
        return name;
    }

    public Integer getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Map<String, URL> getActions() {
        return actions;
    }

    public Map<String, String> getInformation() {
        return information;
    }

    @Override
    public String toString() {
        return "Device [" + name
               + " ("
               + type
               + "."
               + id
               + ") information="
               + information
               + " actions="
               + actions
               + "]";
    }
}
