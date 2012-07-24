package com.rhinoforms;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import com.rhinoforms.serverside.InputPojo;

@SuppressWarnings("serial")
public class FormServlet extends HttpServlet {

	private static final Logger LOGGER = Logger.getLogger(FormServlet.class);

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		HttpSession session = request.getSession();
		
		int flowId = Integer.parseInt(request.getParameter(Constants.FLOW_ID_FIELD_NAME));
		
		FormFlow flow = SessionHelper.getFlow(flowId, session);
		Context jsContext = Context.enter();
		Scriptable scope = flow.getScope();
		Map<String, String> data = new HashMap<String, String>();

		if (scope != null) {
			List<InputPojo> inputPOJOs = flow.getCurrentInputPojos();
			for (InputPojo inputPOJO : inputPOJOs) {
				if (inputPOJO.type.equalsIgnoreCase("checkbox")) {
					inputPOJO.value = request.getParameter(inputPOJO.name) != null ? "true" : "false";
				} else {
					inputPOJO.value = request.getParameter(inputPOJO.name);
				}
				data.put(inputPOJO.name, inputPOJO.value);	
			}
			
			String jsPojoMap = inputPOJOListtoJS(inputPOJOs);
			LOGGER.debug("inputPojos as js:" + jsPojoMap);
			
			StringBuilder commandStringBuilder = new StringBuilder();
			commandStringBuilder.append("rf.validateFields(");
			commandStringBuilder.append(jsPojoMap);
			commandStringBuilder.append(")");
			
			NativeArray errors = (NativeArray) jsContext.evaluateString(scope, commandStringBuilder.toString(), "<cmd>", 1, null);
			for (int i = 0; i < errors.getLength(); i++) {
				Object error = errors.get(i, scope);
				LOGGER.info("Error - " + error);
			}
			
			
			// Build XML from submitted values
			StringBuilder xmlStringBuilder = new StringBuilder();
			xmlStringBuilder.append("<data>\n");
			for (String fieldName : data.keySet()) {
				String value = data.get(fieldName);
				if (value != null) {
					xmlStringBuilder.append("<").append(fieldName).append(">");
					xmlStringBuilder.append(value);
					xmlStringBuilder.append("</").append(fieldName).append(">\n");
				}
			}
			xmlStringBuilder.append("</data>\n");
			PrintWriter writer = response.getWriter();
			writer.write(xmlStringBuilder.toString());
		} else {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "Your session has expired.");
		}
	}
	
	public static void main(String[] args) {
		Context jsContext2 = Context.enter();
		ScriptableObject scope = jsContext2.initStandardObjects();
		Object wrappedOut = Context.javaToJS(System.out, scope);
		ScriptableObject.putProperty(scope, "out", wrappedOut);
		
		jsContext2.evaluateString(scope, "var list = [{\"a\": \"one\"}]; out.print(list[0].a);", "<cmd>", 1, null);
	}
	
	private String inputPOJOListtoJS(List<InputPojo> inputPojos) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("{");
		boolean first = true;
		for (InputPojo inputPOJO : inputPojos) {
			// { name:name, value:value, validation:validation, validationFunction:validationFunction };
			if (first) {
				first = false;
			} else {
				stringBuilder.append(",");
			}
			stringBuilder.append("\"");
			stringBuilder.append(inputPOJO.name);
			stringBuilder.append("\":");
			stringBuilder.append("{");
			stringBuilder.append("name:\"");
			stringBuilder.append(inputPOJO.name);
			stringBuilder.append("\",");
			stringBuilder.append("value:");
			if (inputPOJO.type.equalsIgnoreCase("checkbox")) {
				stringBuilder.append(inputPOJO.value);
			} else {
				stringBuilder.append("\"");
				stringBuilder.append(inputPOJO.value);
				stringBuilder.append("\"");
			}
			if (inputPOJO.validation != null) {
				stringBuilder.append(",validation:\"");
				stringBuilder.append(inputPOJO.validation);
				stringBuilder.append("\"");
			}
			if (inputPOJO.validationFunction != null) {
				stringBuilder.append(",validationFunction:\"");
				stringBuilder.append(inputPOJO.validationFunction.replaceAll("\"", "'"));
				stringBuilder.append("\"");
			}
			stringBuilder.append("}");
		}
		stringBuilder.append("}");
		return stringBuilder.toString();
	}
	
}
