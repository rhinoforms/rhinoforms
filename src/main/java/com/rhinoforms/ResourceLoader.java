package com.rhinoforms;

import java.io.FileNotFoundException;
import java.io.InputStream;

public interface ResourceLoader {

	InputStream getResourceAsStream(String path) throws FileNotFoundException;
	
}
