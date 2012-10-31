package com.rhinoforms.resourceloader;

import java.io.IOException;
import java.io.InputStream;

public interface SingleSourceResourceLoader {

	void initialise(String resourcesSource) throws ResourceLoaderException;
	
	InputStream getResourceAsStream(String path) throws IOException;
	
}
