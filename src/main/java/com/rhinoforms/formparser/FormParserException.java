package com.rhinoforms.formparser;

public class FormParserException extends Exception {

	private static final long serialVersionUID = 1651878276630684846L;

	public FormParserException(String message, Throwable cause) {
		super(message, cause);
	}

	public FormParserException(String message) {
		super(message);
	}

	public FormParserException(Throwable cause) {
		super(cause);
	}

}
