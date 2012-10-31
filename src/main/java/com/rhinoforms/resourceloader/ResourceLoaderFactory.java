package com.rhinoforms.resourceloader;

import javax.servlet.ServletContext;

public class ResourceLoaderFactory {
	
	private ServletContext servletContext;

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
				newResourceLoader = new DevResourceLoader(servletContext);
			} else {
				try {
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
			newResourceLoader.initialise(formResourcesSource);
			return newResourceLoader;
		} else {
			throw new RuntimeException("Property formResourcesSource not configured.");
		}
	}
	
}
