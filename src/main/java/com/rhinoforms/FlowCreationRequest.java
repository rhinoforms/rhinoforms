package com.rhinoforms;


public class FlowCreationRequest {

	private String formFlowPath;
	private String initData;
	private boolean suppressDebugBar;
	
	public FlowCreationRequest() {
	}
	
	public FlowCreationRequest(String formFlowPath) {
		this.formFlowPath = formFlowPath;
	}
	
	public FlowCreationRequest(String formFlowPath, String initData) {
		this.formFlowPath = formFlowPath;
		this.initData = initData;
	}

	public String getFormFlowPath() {
		return formFlowPath;
	}

	public void setFormFlowPath(String formFlowPath) {
		this.formFlowPath = formFlowPath;
	}

	public String getInitData() {
		return initData;
	}

	public void setInitData(String initData) {
		this.initData = initData;
	}

	public boolean isSuppressDebugBar() {
		return suppressDebugBar;
	}

	public void setSuppressDebugBar(boolean suppressDebugBar) {
		this.suppressDebugBar = suppressDebugBar;
	}

}
