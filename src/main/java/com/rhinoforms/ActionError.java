package com.rhinoforms;

@SuppressWarnings("serial")
public class ActionError extends Exception {

	public ActionError(String message) {
		super(message);
	}

	public ActionError(String message, Throwable cause) {
		super(message, cause);
	}
	
}
