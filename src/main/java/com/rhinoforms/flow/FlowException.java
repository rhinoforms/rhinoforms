package com.rhinoforms.flow;

@SuppressWarnings("serial")
public class FlowException extends Exception {

	private String frontendMessage;

	public FlowException(String message, Throwable cause) {
		super(message, cause);
	}

	public FlowException(String message) {
		super(message);
	}

	public FlowException(String message, String frontendMessage) {
		super(message);
		this.frontendMessage = frontendMessage;
	}

	public FlowException(String message, String frontendMessage, Throwable cause) {
		super(message, cause);
		this.frontendMessage = frontendMessage;
	}

	public String getFrontendMessage() {
		return frontendMessage;
	}

	public void setFrontendMessage(String frontendMessage) {
		this.frontendMessage = frontendMessage;
	}

}
