package com.rhinoforms.flow;

@SuppressWarnings("serial")
public class RemoteSubmissionHelperException extends FlowExceptionActionError {

	public RemoteSubmissionHelperException(String message) {
		super(message);
	}

	public RemoteSubmissionHelperException(String message, String frontendMessage) {
		super(message, frontendMessage);
	}
	
	public RemoteSubmissionHelperException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public RemoteSubmissionHelperException(String message, String frontendMessage, Throwable cause) {
		super(message, frontendMessage, cause);
	}
	
}
