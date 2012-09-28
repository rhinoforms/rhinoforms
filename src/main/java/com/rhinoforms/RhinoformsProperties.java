package com.rhinoforms;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class RhinoformsProperties {

	private static RhinoformsProperties instance;
	private boolean showDebugBar;
	
	public static RhinoformsProperties getInstance() {
		if (instance == null) {
			synchronized (RhinoformsProperties.class) {
				if (instance == null) {
					instance = createInstance();
				}
			}
		}
		return instance;
	}

	private static RhinoformsProperties createInstance() {
		RhinoformsProperties rhinoformsProperties = new RhinoformsProperties();
		String filename = "/Rhinoforms.properties";
		InputStream propertiesStream = RhinoformsProperties.class.getResourceAsStream(filename);
		if (propertiesStream != null) {
			Properties properties = new Properties();
			try {
				properties.load(propertiesStream);
				rhinoformsProperties.setShowDebugBar("true".equalsIgnoreCase(properties.getProperty("showDebugBar")));
				return rhinoformsProperties;
			} catch (IOException e) {
				throw new RuntimeException("Failed to load " + filename);
			}
		} else {
			throw new RuntimeException("Failed to find " + filename);
		}
	}
	
	static void setInstance(RhinoformsProperties instance) {
		RhinoformsProperties.instance = instance;
	}
	
	public boolean isShowDebugBar() {
		return showDebugBar;
	}
	
	public void setShowDebugBar(boolean showDebugBar) {
		this.showDebugBar = showDebugBar;
	}
	
}
