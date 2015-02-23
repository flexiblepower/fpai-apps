package org.flexiblepower.protocol.mielegateway.xml;

import org.flexiblepower.protocol.mielegateway.api.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ActionResultParser {
    private static final Logger log = LoggerFactory.getLogger(ActionResultParser.class);

    private static final ActionResultParser instance = new ActionResultParser();

    public static final ActionResultParser get() {
        return instance;
    }

    public ActionResult parse(Element element) {
        boolean isOk;
        if ("ok".equals(element.getNodeName())) {
            isOk = true;
        } else if ("error".equals(element.getNodeName())) {
            isOk = false;
        } else {
            log.warn("The root element is not <ok> or <error>");
            return null;
        }

        String message = null, type = null;

        NodeList nodes = element.getChildNodes();
        for (int ix = 0; ix < nodes.getLength(); ix++) {
            Node node = nodes.item(ix);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                if (node.getNodeName().equals("message")) {
                    message = node.getTextContent();
                } else if (node.getNodeName().equals("error-type")) {
                    type = node.getTextContent();
                }
            }
        }

        if (message == null) {
            log.warn("Missing the message");
            return null;
        } else {
            return new ActionResult(isOk, message, type);
        }
    }
}
