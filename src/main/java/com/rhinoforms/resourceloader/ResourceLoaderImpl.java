package com.rhinoforms.resourceloader;

import java.io.IOException;
import java.io.InputStream;

public class ResourceLoaderImpl implements ResourceLoader {

	private SingleSourceResourceLoader webappResourceLoader;
	private SingleSourceResourceLoader formResourceLoader;

	public ResourceLoaderImpl(SingleSourceResourceLoader webappResourceLoader, SingleSourceResourceLoader formResourceLoader) throws ResourceLoaderException {
		this.webappResourceLoader = webappResourceLoader;
		this.formResourceLoader = formResourceLoader;
	}

	@Override
	public InputStream getWebappResourceAsStream(String path) throws IOException {
		return webappResourceLoader.getResourceAsStream(path);
	}

	@Override
	public InputStream getFormResourceAsStream(String path) throws IOException {
		return formResourceLoader.getResourceAsStream(path);
	}

}
