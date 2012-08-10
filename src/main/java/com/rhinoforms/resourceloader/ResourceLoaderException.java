package com.rhinoforms.resourceloader;

@SuppressWarnings("serial")
public class ResourceLoaderException extends Exception {

	public ResourceLoaderException(String message, Throwable cause) {
		super(message, cause);
	}

	public ResourceLoaderException(String message) {
		super(message);
	}

}
