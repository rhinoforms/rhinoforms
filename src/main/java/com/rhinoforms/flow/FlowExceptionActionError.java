package com.rhinoforms.flow;

@SuppressWarnings("serial")
public class FlowExceptionActionError extends FlowException {

	public FlowExceptionActionError(String message) {
		super(message);
	}

	public FlowExceptionActionError(String message, Throwable cause) {
		super(message, cause);
	}
	
}
