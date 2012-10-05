package com.rhinoforms;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class FlowAction implements Serializable {
	
	private String name;
	private String target;
	private String type;
	private Map<String, String> params;
	private Submission submission;
	private boolean clearTargetFormDocBase;
	private static final long serialVersionUID = -7126508228396096955L;
	
	public FlowAction(String name, String target) {
		setName(name);
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

	public FlowActionType getType() {
		if (type == null) {
			return FlowActionType.safeValueOf(name);
		} else {
			return FlowActionType.safeValueOf(type);
		}
	}
	
	public void setType(String type) {
		this.type = type;
	}
	
	public Map<String, String> getParams() {
		return params;
	}

	public void setParams(Map<String, String> params) {
		this.params = params;
	}

	public Submission getSubmission() {
		return submission;
	}
	
	public void setSubmission(Submission submission) {
		this.submission = submission;
	}
	
	public boolean isClearTargetFormDocBase() {
		return clearTargetFormDocBase;
	}
	
	public void setClearTargetFormDocBase(boolean clearTargetFormDocBase) {
		this.clearTargetFormDocBase = clearTargetFormDocBase;
	}
	
}
