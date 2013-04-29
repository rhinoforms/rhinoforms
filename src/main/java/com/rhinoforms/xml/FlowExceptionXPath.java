package com.rhinoforms.xml;

import com.rhinoforms.flow.FlowException;

@SuppressWarnings("serial")
public class FlowExceptionXPath extends FlowException {

	public FlowExceptionXPath(String message, Throwable cause) {
		super(message, cause);
	}

	public FlowExceptionXPath(String message) {
		super(message);
	}
	
}
