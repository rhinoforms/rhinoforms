package com.rhinoforms.resourceloader;

import java.io.IOException;
import java.io.InputStream;

public interface ResourceLoader {

	InputStream getWebappResourceAsStream(String path) throws IOException;
	
	InputStream getFormResourceAsStream(String path) throws IOException;

	void formResourcesChanged() throws ResourceLoaderException;
	
}
