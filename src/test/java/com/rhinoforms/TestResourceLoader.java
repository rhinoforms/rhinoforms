package com.rhinoforms;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import com.rhinoforms.resourceloader.ResourceLoader;

public class TestResourceLoader implements ResourceLoader {

	@Override
	public InputStream getResourceAsStream(String path) throws FileNotFoundException {
		return new FileInputStream("src/main/webapp/" + path);
	}

}
