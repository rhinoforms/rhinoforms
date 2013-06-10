package com.rhinoforms.flow;

@SuppressWarnings("serial")
public class FlowExceptionActionError extends FlowException {

	public FlowExceptionActionError(String message) {
		super(message);
	}

	public FlowExceptionActionError(String message, String frontendMessage) {
		super(message, frontendMessage);
	}

	public FlowExceptionActionError(String message, Throwable cause) {
		super(message, cause);
	}

	public FlowExceptionActionError(String message, String frontendMessage, Throwable cause) {
		super(message, frontendMessage, cause);
	}

}
