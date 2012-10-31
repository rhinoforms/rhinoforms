package com.rhinoforms;

import javax.servlet.ServletContext;

import com.rhinoforms.resourceloader.ClasspathResourceLoader;
import com.rhinoforms.resourceloader.ResourceLoader;
import com.rhinoforms.resourceloader.ResourceLoaderException;
import com.rhinoforms.resourceloader.ResourceLoaderFactory;
import com.rhinoforms.resourceloader.ResourceLoaderImpl;
import com.rhinoforms.resourceloader.ServletContextResourceLoader;
import com.rhinoforms.resourceloader.SingleSourceResourceLoader;

public class ApplicationContext {

	private RhinoformsProperties rhinoformsProperties;
	private ResourceLoader resourceLoader;
	private ServletContext servletContext;

	public ApplicationContext(ServletContext servletContext) throws ResourceLoaderException {
		this.servletContext = servletContext;
		this.rhinoformsProperties = RhinoformsProperties.getInstance();
		this.resourceLoader = createResourceLoader();
	}

	public ResourceLoader getResourceLoader() {
		return resourceLoader;
	}
	
	private ResourceLoader createResourceLoader() throws ResourceLoaderException {
		String formResourcesSource = rhinoformsProperties.getFormResourceLoader();
		
		SingleSourceResourceLoader webappResourceLoader = new ServletContextResourceLoader(servletContext);
		SingleSourceResourceLoader formResourceLoader;
		
		if (formResourcesSource != null && !formResourcesSource.isEmpty()) {
			formResourceLoader = new ResourceLoaderFactory(servletContext).createResourceLoader(formResourcesSource);
		} else {
			formResourceLoader = new ClasspathResourceLoader();
		}
		return new ResourceLoaderImpl(webappResourceLoader, formResourceLoader);
	}
	
}
