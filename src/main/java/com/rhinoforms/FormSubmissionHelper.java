package com.rhinoforms;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.servlet.ServletException;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rhinoforms.js.JSSerialiser;
import com.rhinoforms.serverside.InputPojo;

public class FormSubmissionHelper {

	private JSMasterScope masterScope;
	private JSSerialiser jsSerialiser;
	private DocumentHelper documentHelper;
	private final Logger logger = LoggerFactory.getLogger(FormSubmissionHelper.class);
	private static final String UTF8 = "UTF-8";

	public FormSubmissionHelper(JSMasterScope masterScope) {
		this.masterScope = masterScope;
		this.jsSerialiser = new JSSerialiser();
		this.documentHelper = new DocumentHelper();
	}
	
	public String collectActionParameters(Map<String, String> actionParams, Map<String, String> parameterMap) {
		//   from rf.action.xxx parameters
		getMatchingParamsStripPrefix(actionParams, "rf.action.", parameterMap);
		//   and from after the ? in the rf.action parameter string
		String action = parameterMap.get("rf.action");
		if (action.contains("?")) {
			String[] actionParts = action.split("\\?");
			action = actionParts[0];
			String actionParamsString = actionParts[1];
			Map<String, String> actionParamsFromString = paramsStringToMap(actionParamsString);
			actionParams.putAll(actionParamsFromString);
		}
		return action;
	}
	
	public Set<String> validateAndPersist(FormFlow formFlow, String action, Map<String, String> parameterMap) throws ServletException {
		Set<String> fieldsInError = new HashSet<String>();

		// Collect input values
		List<InputPojo> inputPOJOs = formFlow.getCurrentInputPojos();
		for (InputPojo inputPOJO : inputPOJOs) {
			if (inputPOJO.getType().equalsIgnoreCase("checkbox")) {
				inputPOJO.setValue(parameterMap.get(inputPOJO.getName()) != null ? "true" : "false");
			} else {
				inputPOJO.setValue(parameterMap.get(inputPOJO.getName()));
			}
		}

		// Validate input
		String jsPojoMap = jsSerialiser.inputPOJOListToJS(inputPOJOs);
		logger.debug("inputPojos as js:{}", jsPojoMap);
		StringBuilder commandStringBuilder = new StringBuilder();
		commandStringBuilder.append("rf.validateFields(");
		commandStringBuilder.append(jsPojoMap);
		commandStringBuilder.append(")");
		Context jsContext = Context.enter();
		Scriptable scope = masterScope.createWorkingScope();
		try {
			NativeArray errors = (NativeArray) jsContext.evaluateString(scope, commandStringBuilder.toString(), "<cmd>", 1, null);
			for (int i = 0; i < errors.getLength(); i++) {
				ScriptableObject error = (ScriptableObject) errors.get(i, scope);
				fieldsInError.add(error.get("name", scope).toString());
			}
		} catch (EcmaError e) {
			String message = "";
			logger.error(message, e);
			throw new ServletException(message, e);
		} finally {
			Context.exit();
		}

		// If Back action remove any invalid input
		if (action.equals(FormFlow.BACK_ACTION) && !fieldsInError.isEmpty()) {
			HashSet<InputPojo> inputPOJOsToRemove = new HashSet<InputPojo>();
			for (InputPojo inputPojo : inputPOJOs) {
				if (fieldsInError.contains(inputPojo.getName())) {
					inputPOJOsToRemove.add(inputPojo);
				}
			}
			inputPOJOs.removeAll(inputPOJOsToRemove);
			fieldsInError.clear();
		}

		if (fieldsInError.isEmpty()) {
			// Persist collected form data
			String docBase = formFlow.getDocBase();
			try {
				documentHelper.persistFormData(inputPOJOs, docBase, formFlow.getDataDocument());
			} catch (DocumentHelperException e) {
				String message = "Failed to map field to xml document.";
				logger.error(message, e);
				throw new ServletException(message, e);
			}
		}

		return fieldsInError;
	}
	
	private void getMatchingParamsStripPrefix(Map<String, String> actionParams, String prefix, Map<String, String> parameterMap) {
		for (String parameterName : parameterMap.keySet()) {
			if (parameterName.startsWith(prefix)) {
				actionParams.put(parameterName.substring(prefix.length()), parameterMap.get(parameterName));
			}
		}
	}
	
	private Map<String, String> paramsStringToMap(String params) {
		Map<String, String> paramsMap = new HashMap<String, String>();
		if (params != null) {
			StringTokenizer st = new StringTokenizer(params, "&");
			while (st.hasMoreTokens()) {
				String token = st.nextToken();
				if (token.indexOf("=") > -1) {
					String paramName = token.substring(0, token.indexOf("="));
					String paramValue = token.substring(token.indexOf("=") + 1);
					try {
						paramValue = URLDecoder.decode(paramValue, UTF8);
					} catch (UnsupportedEncodingException e) {
						logger.warn("UnsupportedEncodingException while decoding paramValue:'{}', using " + UTF8, paramValue, e);
					}
					paramsMap.put(paramName, paramValue);
				}
			}
		}
		return paramsMap;
	}
	
}
