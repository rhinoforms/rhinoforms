package com.rhinoforms.flow;

public class FormSubmissionResult {

	private String nextUrl;
	private int httpErrorCode;
	private String errorMessage;

	public void setError(int httpErrorCode, String errorMessage) {
		this.httpErrorCode= httpErrorCode;
		this.errorMessage = errorMessage;
	}
	
	public boolean isError() {
		return errorMessage != null;
	}
	
	public String getNextUrl() {
		return nextUrl;
	}

	public void setNextUrl(String nextUrl) {
		this.nextUrl = nextUrl;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public int getHttpErrorCode() {
		return httpErrorCode;
	}

	public void setHttpErrorCode(int httpErrorCode) {
		this.httpErrorCode = httpErrorCode;
	}

}
