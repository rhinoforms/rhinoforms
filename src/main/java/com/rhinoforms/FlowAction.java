package com.rhinoforms;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class FlowAction implements Serializable {
	
	private String name;
	private String target;
	private Map<String, String> params;
	private static final long serialVersionUID = -7126508228396096955L;

	public FlowAction(String name, String target) {
		this.name = name;
		this.target = target;
	}

	public void addParam(String name, String value) {
		if (params == null) {
			params = new HashMap<String, String>();
		}
		params.put(name, value);
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public Map<String, String> getParams() {
		return params;
	}

	public void setParams(Map<String, String> params) {
		this.params = params;
	}

}
