package com.rhinoforms;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
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
import org.mozilla.javascript.WrappedException;
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

		// Collect input values
		List<InputPojo> inputPOJOs = formFlow.getCurrentInputPojos();
		for (InputPojo inputPOJO : inputPOJOs) {
			if (inputPOJO.getType().equalsIgnoreCase("checkbox")) {
				inputPOJO.setValue(parameterMap.get(inputPOJO.getName()) != null ? "true" : "false");
			} else {
				inputPOJO.setValue(parameterMap.get(inputPOJO.getName()));
			}
		}
		
		List<InputPojo> includeFalseInputs = getIncludeFalseInputs(inputPOJOs);
		inputPOJOs.removeAll(includeFalseInputs);

		Set<String> fieldsInError = validateInput(inputPOJOs);

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
				documentHelper.clearFormData(includeFalseInputs, docBase, formFlow.getDataDocument());
			} catch (DocumentHelperException e) {
				String message = "Failed to map field to xml document.";
				logger.error(message, e);
				throw new ServletException(message, e);
			}
		}

		return fieldsInError;
	}

	List<InputPojo> getIncludeFalseInputs(List<InputPojo> inputPojos) {
		List<InputPojo> includeFalseInputPojos = new ArrayList<InputPojo>();
		List<InputPojo> inputsWithIncludeIfStatements = new ArrayList<InputPojo>();
		for (InputPojo inputPojo : inputPojos) {
			if (inputPojo.getRfAttributes().containsKey(Constants.INCLUDE_IF_ATTR)) {
				inputsWithIncludeIfStatements.add(inputPojo);
			}
		}
		if (!inputsWithIncludeIfStatements.isEmpty()) {
			String jsPojoMapString = jsSerialiser.inputPOJOListToJS(inputPojos);
			Scriptable workingScope = masterScope.createWorkingScope();
			Context context = masterScope.getCurrentContext();
			context.evaluateString(workingScope, "var fields = " + jsPojoMapString, "Add fields to scope", 1, null);
			for (InputPojo inputPojo : inputsWithIncludeIfStatements) {
				String inputName = inputPojo.getName();
				String includeIfStatement = inputPojo.getRfAttributes().get(Constants.INCLUDE_IF_ATTR);
				Object object = context.evaluateString(workingScope, includeIfStatement, inputName + " includeif statement", 1, null);
				boolean b = Context.toBoolean(object);
				logger.debug("input includeif name:'{}', result:'{}'", inputName, b);
				if (!b) {
					includeFalseInputPojos.add(inputPojo);
				}
			}
		}
		return includeFalseInputPojos;
	}

	Set<String> validateInput(List<InputPojo> inputPojos) throws ServletException {
		Set<String> fieldsInError = new HashSet<String>();
		String jsPojoMapString = jsSerialiser.inputPOJOListToJS(inputPojos);
		logger.debug("inputPojos as js:{}", jsPojoMapString);
		StringBuilder commandStringBuilder = new StringBuilder();
		commandStringBuilder.append("rf.validateFields(");
		commandStringBuilder.append(jsPojoMapString);
		commandStringBuilder.append(")");
		Scriptable scope = masterScope.createWorkingScope();
		Context jsContext = Context.getCurrentContext();
		try {
			NativeArray errors = (NativeArray) jsContext.evaluateString(scope, commandStringBuilder.toString(), "<cmd>", 1, null);
			for (int i = 0; i < errors.getLength(); i++) {
				ScriptableObject error = (ScriptableObject) errors.get(i, scope);
				String errorFieldName = error.get("name", scope).toString();
				String errorFieldMessage = error.get("message", scope).toString();
				logger.info("Input error, name:'{}', message:'{}'", errorFieldName, errorFieldMessage);
				fieldsInError.add(errorFieldName);
			}
		} catch (EcmaError e) {
			String message = "EcmaError error while calling Javascript function rf.validateFields.";
			logger.error(message, e);
			throw new ServletException(message, e);
		} catch (WrappedException e) {
			String message = "WrappedException error while calling Javascript function rf.validateFields.";
			logger.error(message, e);
			throw new ServletException(message, e);
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
