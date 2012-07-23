package com.kaicube.simpleform;

import javax.servlet.http.HttpSession;

public class SessionHelper {

	public static void addFlow(FormFlow formFlow, HttpSession session) {
		session.setAttribute("FormFlow" + formFlow.getId(), formFlow);
	}
	
	public static FormFlow getFlow(int id, HttpSession session) {
		return (FormFlow) session.getAttribute("FormFlow" + id);
	}
	
}
