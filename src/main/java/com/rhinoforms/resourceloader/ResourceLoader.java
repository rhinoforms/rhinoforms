package com.rhinoforms.resourceloader;

import java.io.FileNotFoundException;
import java.io.InputStream;

public interface ResourceLoader {

	InputStream getResourceAsStream(String path) throws FileNotFoundException;
	
}
