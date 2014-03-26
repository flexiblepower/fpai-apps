package org.flexiblepower.protocol.mielegateway.api;


public class ActionResult {
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
