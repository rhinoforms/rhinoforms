package com.rhinoforms.util;

import java.util.HashMap;
import java.util.Map;

public class ServletHelper {
	
	public Map<String, String> mapOfArraysToMapOfFirstValues(Map<String, String[]> parameterMapMultiValue) {
		HashMap<String, String> params = new HashMap<>();
		for (String key : parameterMapMultiValue.keySet()) {
			params.put(key, parameterMapMultiValue.get(key)[0]);
		}
		return params;
	}
	
}
