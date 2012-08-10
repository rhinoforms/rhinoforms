package com.rhinoforms.resourceloader;

import java.io.InputStream;

import javax.servlet.ServletContext;

public class ServletResourceLoader implements ResourceLoader {

	private ServletContext servletContext;

	public ServletResourceLoader(ServletContext servletContext) {
		this.servletContext = servletContext;
	}
	
	@Override
	public InputStream getResourceAsStream(String path) {
		return servletContext.getResourceAsStream(path);
	}

}
