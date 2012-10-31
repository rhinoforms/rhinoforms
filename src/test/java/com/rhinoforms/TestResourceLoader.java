package com.rhinoforms;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import com.rhinoforms.resourceloader.SingleSourceResourceLoader;
import com.rhinoforms.resourceloader.ResourceLoaderException;

public class TestResourceLoader implements SingleSourceResourceLoader {

	@Override
	public InputStream getResourceAsStream(String path) throws FileNotFoundException {
		File file = new File("src/main/webapp/" + path);
		if (!file.isFile()) {
			file = new File("src/test/resources/" + path);
		}
		if (!file.isFile()) {
			file = new File("src/main/resources/" + path);
		}
		if (!file.isFile()) {
			throw new FileNotFoundException("File not found in 'src/main/webapp/' or 'src/test/resources/', path: '" + path + "'");
		}
		return new FileInputStream(file);
	}

	@Override
	public void initialise(String resourcesSource) throws ResourceLoaderException {
	}

}
