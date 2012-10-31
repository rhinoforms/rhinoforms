package com.rhinoforms.resourceloader;

import java.io.IOException;
import java.io.InputStream;

public class ClasspathResourceLoader implements SingleSourceResourceLoader {

	@Override
	public void initialise(String resourcesSource) throws ResourceLoaderException {
	}

	@Override
	public InputStream getResourceAsStream(String path) throws IOException {
		if (path.charAt(0) != '/') {
			path = "/" + path;
		}
		return ClasspathResourceLoader.class.getResourceAsStream(path);
	}

}
