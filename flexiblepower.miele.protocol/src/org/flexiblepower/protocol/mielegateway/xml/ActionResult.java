package org.flexiblepower.protocol.mielegateway.xml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ActionResult {
	private static final Logger log = LoggerFactory
			.getLogger(ActionResult.class);

	public static ActionResult parse(Element element) {
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

	private final boolean isOk;
	private final String message;
	private final String errorType;

	public ActionResult(boolean isOk, String message, String errorType) {
		this.isOk = isOk;
		this.message = message;
		this.errorType = errorType;
	}

	public String getErrorType() {
		return errorType;
	}

	public String getMessage() {
		return message;
	}

	public boolean isOk() {
		return isOk;
	}

	@Override
	public String toString() {
		return "ActionResult [" + (isOk ? "ok" : "error") + ": " + message
				+ (errorType == null ? "" : " (" + errorType + ")") + "]";
	}
}
