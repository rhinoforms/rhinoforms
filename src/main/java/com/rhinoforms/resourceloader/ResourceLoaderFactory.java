package com.rhinoforms.resourceloader;

public class ResourceLoaderFactory {
	
	public SingleSourceResourceLoader createResourceLoader(String formResourcesSource) throws ResourceLoaderException {
		formResourcesSource = formResourcesSource.trim();
		if (formResourcesSource != null && !formResourcesSource.isEmpty()) {
			String singleSourceResourceLoaderClassName = formResourcesSource.split(" ")[0];
			singleSourceResourceLoaderClassName = singleSourceResourceLoaderClassName.replaceAll("-", "");
			try {
				@SuppressWarnings("unchecked")
				Class<SingleSourceResourceLoader> resourceLoaderClass = (Class<SingleSourceResourceLoader>) Class
						.forName(singleSourceResourceLoaderClassName);
				SingleSourceResourceLoader newResourceLoader = resourceLoaderClass.newInstance();
				newResourceLoader.initialise(formResourcesSource);
				return newResourceLoader;
			} catch (ClassNotFoundException e) {
				throw new RuntimeException("Could not find ResourceLoader matching the given formResourcesSource type.", e);
			} catch (InstantiationException e) {
				throw new RuntimeException("Failed to create ResourceLoader.", e);
			} catch (IllegalAccessException e) {
				throw new RuntimeException("Failed to create ResourceLoader.", e);
			}
		} else {
			throw new RuntimeException("Property formResourcesSource not configured.");
		}
	}
	
}
