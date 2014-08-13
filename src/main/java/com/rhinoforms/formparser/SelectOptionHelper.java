package com.rhinoforms.formparser;

import com.rhinoforms.resourceloader.ResourceLoader;
import com.rhinoforms.resourceloader.ResourceLoaderException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class SelectOptionHelper {

	private ResourceLoader resourceLoader;

	public SelectOptionHelper(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	public List<SelectOptionPojo> loadOptions(String source) throws ResourceLoaderException {
		List<SelectOptionPojo> options = new ArrayList<>();
		boolean csv = source.toLowerCase().endsWith(".csv");
		try {
			InputStream resourceAsStream = resourceLoader.getFormResourceAsStream(source);
			if (resourceAsStream != null) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream));
				while (reader.ready()) {
					String line = reader.readLine();
					if (csv) {
						String[] split = line.split("(?<!\\\\),");
						if (split.length > 1) {
							options.add(new SelectOptionPojo(prepareValue(split[1]), prepareValue(split[0])));
						} else {
							options.add(new SelectOptionPojo(prepareValue(split[0])));
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

	private String prepareValue(String value) {
		return value.trim().replace("\\,", ",");
	}

}
