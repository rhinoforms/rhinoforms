package com.rhinoforms.flow;

@SuppressWarnings("serial")
public class FlowException extends Exception {

	public FlowException(String message, Throwable cause) {
		super(message, cause);
	}

	public FlowException(String message) {
		super(message);
	}
	
}
