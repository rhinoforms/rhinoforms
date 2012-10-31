package com.rhinoforms.resourceloader;

import java.io.FileNotFoundException;
import java.io.InputStream;

import javax.servlet.ServletContext;

public class ServletContextResourceLoader implements SingleSourceResourceLoader {

	private ServletContext servletContext;

	public ServletContextResourceLoader(ServletContext servletContext) {
		this.servletContext = servletContext;
	}
	
	@Override
	public InputStream getResourceAsStream(String path) throws FileNotFoundException {
		InputStream stream = servletContext.getResourceAsStream(path);
		if (stream != null) {
			return stream;
		} else {
			throw new FileNotFoundException(path);
		}
	}

	@Override
	public void initialise(String resourcesSource) {
	}

}
