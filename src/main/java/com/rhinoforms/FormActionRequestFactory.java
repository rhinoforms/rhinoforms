package com.rhinoforms;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.rhinoforms.util.ServletHelper;
import com.rhinoforms.util.StringUtils;

public class FormActionRequestFactory {

	private ServletHelper servletHelper;
	
	public FormActionRequestFactory(ServletHelper servletHelper) {
		this.servletHelper = servletHelper;
	}

	public FormActionRequest createRequest(HttpServletRequest servletRequest) {
		FormActionRequest formActionRequest = new FormActionRequest();
		
		@SuppressWarnings("unchecked")
		Map<String, String[]> parameterMapMultiValue = servletRequest.getParameterMap();
		formActionRequest.setParameterMap(servletHelper.mapOfArraysToMapOfFirstValues(parameterMapMultiValue));
		formActionRequest.setSuppressDebugBar(StringUtils.isStringTrueNullSafe(servletRequest.getParameter(Constants.SUPPRESS_DEBUG_BAR_PARAM)));
		return formActionRequest;
	}

}
