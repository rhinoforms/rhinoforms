package com.rhinoforms.resourceloader;

import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceLoaderFactory {
	
	private ServletContext servletContext;
	private static final Logger LOGGER = LoggerFactory.getLogger(ResourceLoaderFactory.class);

	public ResourceLoaderFactory(ServletContext servletContext) {
		this.servletContext = servletContext;
	}
	
	public SingleSourceResourceLoader createResourceLoader(String formResourcesSource) throws ResourceLoaderException {
		formResourcesSource = formResourcesSource.trim();
		if (formResourcesSource != null && !formResourcesSource.isEmpty()) {
			String singleSourceResourceLoaderClassName = formResourcesSource.split(" ")[0];
			singleSourceResourceLoaderClassName = singleSourceResourceLoaderClassName.replaceAll("-", "");
			
			SingleSourceResourceLoader newResourceLoader;
			if (singleSourceResourceLoaderClassName.equals("com.rhinoforms.resourceloader.DevResourceLoader")) {
				LOGGER.info("Using DevResourceLoader as the formResourceLoader.");
				newResourceLoader = new DevResourceLoader(servletContext);
			} else {
				try {
					LOGGER.info("Loading formResourceLoader class '" + singleSourceResourceLoaderClassName + "'.");
					@SuppressWarnings("unchecked")
					Class<SingleSourceResourceLoader> resourceLoaderClass = (Class<SingleSourceResourceLoader>) Class
					.forName(singleSourceResourceLoaderClassName);
					newResourceLoader = resourceLoaderClass.newInstance();
				} catch (ClassNotFoundException e) {
					throw new RuntimeException("Could not find ResourceLoader matching the given formResourcesSource type.", e);
				} catch (InstantiationException e) {
					throw new RuntimeException("Failed to create ResourceLoader.", e);
				} catch (IllegalAccessException e) {
					throw new RuntimeException("Failed to create ResourceLoader.", e);
				}
			}
			LOGGER.info("Initialising formResourceLoader.");
			newResourceLoader.initialise(formResourcesSource);
			return newResourceLoader;
		} else {
			throw new RuntimeException("Property formResourcesSource not configured.");
		}
	}
	
}
