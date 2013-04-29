package com.rhinoforms;

import javax.servlet.http.HttpServletRequest;

import com.rhinoforms.util.StringUtils;

public class FlowRequestFactory {

	public FlowCreationRequest createNewFlowRequest(HttpServletRequest servletRequest) {
		FlowCreationRequest flowRequest = new FlowCreationRequest();
		flowRequest.setFormFlowPath(servletRequest.getParameter(Constants.FLOW_PATH_PARAM));
		flowRequest.setInitData(servletRequest.getParameter(Constants.INIT_DATA_PARAM));
		flowRequest.setSuppressDebugBar(StringUtils.isStringTrueNullSafe(servletRequest.getParameter(Constants.SUPPRESS_DEBUG_BAR_PARAM)));
		return flowRequest;
	}
	
}
