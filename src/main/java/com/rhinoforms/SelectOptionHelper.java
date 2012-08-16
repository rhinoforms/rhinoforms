package com.rhinoforms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.rhinoforms.resourceloader.ResourceLoader;
import com.rhinoforms.resourceloader.ResourceLoaderException;

public class SelectOptionHelper {

	private ResourceLoader resourceLoader;

	public SelectOptionHelper(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	public List<SelectOptionPojo> loadOptions(String source) throws ResourceLoaderException {
		List<SelectOptionPojo> options = new ArrayList<SelectOptionPojo>();
		try {
			InputStream resourceAsStream = resourceLoader.getResourceAsStream(source);
			if (resourceAsStream != null) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream));
				while (reader.ready()) {
					options.add(new SelectOptionPojo(reader.readLine()));
				}
			} else {
				throw new ResourceLoaderException("Failed to load select options, source: '" + source + "'");
			}
		} catch (IOException e) {
			throw new ResourceLoaderException("Failed to load select options, source: '" + source + "'", e);
		}
		return options;
	}

	
	
}
