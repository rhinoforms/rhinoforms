package com.rhinoforms;

import java.io.IOException;

import javax.servlet.ServletContext;

import com.rhinoforms.resourceloader.ResourceLoader;
import com.rhinoforms.resourceloader.ResourceLoaderException;
import com.rhinoforms.resourceloader.ResourceLoaderImpl;

public class TestApplicationContext extends ApplicationContext {

	public TestApplicationContext() throws ResourceLoaderException, IOException {
		super(new TestServletContext());
	}
	
	public TestApplicationContext(ServletContext servletContext) throws ResourceLoaderException, IOException {
		super(servletContext);
	}
	
	@Override
	protected ResourceLoader createResourceLoader() throws ResourceLoaderException {
		return new ResourceLoaderImpl(new TestResourceLoader(), new TestResourceLoader());
	}
	
}
