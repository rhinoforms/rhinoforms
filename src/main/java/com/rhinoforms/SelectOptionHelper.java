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
		boolean csv = source.toLowerCase().endsWith(".csv");
		try {
			InputStream resourceAsStream = resourceLoader.getFormResourceAsStream(source);
			if (resourceAsStream != null) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream));
				while (reader.ready()) {
					String line = reader.readLine();
					if (csv) {
						String[] split = line.split(",");
						if (split.length > 1) {
							options.add(new SelectOptionPojo(split[1].trim(), split[0].trim()));
						} else {
							options.add(new SelectOptionPojo(split[0].trim()));
						}
					} else {
						options.add(new SelectOptionPojo(line));
					}
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
