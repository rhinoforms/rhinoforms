package com.rhinoforms;

import javax.servlet.http.HttpSession;

public class SessionHelper {

	public static void setFlow(FormFlow formFlow, HttpSession session) {
		session.setAttribute("FormFlow" + formFlow.getId(), formFlow);
	}
	
	public static FormFlow getFlow(int id, HttpSession session) {
		return (FormFlow) session.getAttribute("FormFlow" + id);
	}
	
}
