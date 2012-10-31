package com.rhinoforms;

import javax.servlet.http.HttpSession;

import com.rhinoforms.flow.FormFlow;

public class SessionHelper {

	public static void setFlow(FormFlow formFlow, HttpSession session) {
		session.setAttribute(getAttributeName(formFlow.getId()), formFlow);
	}

	public static FormFlow getFlow(String id, HttpSession session) {
		return (FormFlow) session.getAttribute(getAttributeName(id));
	}
	
	public static void removeFlow(FormFlow formFlow, HttpSession session) {
		session.removeAttribute(getAttributeName(formFlow.getId()));
	}
	
	private static String getAttributeName(String id) {
		return "FormFlow" + id;
	}
}
