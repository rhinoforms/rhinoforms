package com.rhinoforms.util;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rhinoforms.Constants;

/**
 * This is just for debugging
 */
@SuppressWarnings("serial")
public class TraceServlet extends HttpServlet {

	private static final Logger LOGGER = LoggerFactory.getLogger(TraceServlet.class);
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		logRequest(request);
	}
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		logRequest(request);
	}
	
	private void logRequest(HttpServletRequest request) {
		StringBuilder stringBuilder = new StringBuilder();
		@SuppressWarnings("unchecked")
		int paramCount = arrayMapToString(request.getParameterMap(), stringBuilder);
		stringBuilder.insert(0, Constants.NEW_LINE + "Parameters (" + paramCount + "):" + Constants.NEW_LINE);

		LOGGER.info("{} request{}", request.getMethod(), stringBuilder);
	}
	
	private int arrayMapToString(Map<String, String[]> hashMap, StringBuilder stringBuilder) {
		int count = 0;
		for (String key : hashMap.keySet()) {
			for (String value : hashMap.get(key)) {
				count++;
				stringBuilder.append("    '").append(key).append("':'").append(value).append(Constants.NEW_LINE);
			}
		}
		return count;
	}
	
}
