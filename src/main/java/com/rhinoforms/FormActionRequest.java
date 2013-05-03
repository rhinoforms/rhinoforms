package com.rhinoforms;

import java.util.Map;

public class FormActionRequest {

	private Map<String, String> parameterMap;
	private boolean suppressDebugBar;

	public Map<String, String> getParameterMap() {
		return parameterMap;
	}

	public void setParameterMap(Map<String, String> parameterMap) {
		this.parameterMap = parameterMap;
	}

	public boolean isSuppressDebugBar() {
		return suppressDebugBar;
	}
	
	public void setSuppressDebugBar(boolean suppressDebugBar) {
		this.suppressDebugBar = suppressDebugBar;
	}
	
}
