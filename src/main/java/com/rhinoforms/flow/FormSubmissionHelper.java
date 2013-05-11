package com.rhinoforms.flow;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrappedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rhinoforms.Constants;
import com.rhinoforms.js.FlowExceptionFileNotFound;
import com.rhinoforms.js.JSMasterScope;
import com.rhinoforms.js.JSSerialiser;
import com.rhinoforms.xml.DocumentHelper;
import com.rhinoforms.xml.FlowExceptionXPath;

public class FormSubmissionHelper {

	private JSMasterScope masterScope;
	private JSSerialiser jsSerialiser;
	private DocumentHelper documentHelper;
	private final Logger logger = LoggerFactory.getLogger(FormSubmissionHelper.class);

	public FormSubmissionHelper(JSMasterScope masterScope) {
		this.masterScope = masterScope;
		this.jsSerialiser = new JSSerialiser();
		this.documentHelper = new DocumentHelper();
	}
	
	public FormSubmissionResult handlePost(FormFlow formFlow, Map<String, String> parameterMap)
			throws IOException, FlowExceptionActionError, FlowExceptionBadRequest, FlowExceptionFileNotFound, FlowExceptionJavaScript, FlowExceptionXPath {
		FormSubmissionResult formSubmissionResult = new FormSubmissionResult();
		
		Map<String, String> actionParams = new HashMap<String, String>();
		String actionName = collectActionParameters(actionParams, parameterMap);

		FlowAction flowAction = formFlow.getCurrentActions().get(actionName);
		Set<String> fieldsInError = null;
		if (flowAction != null) {
			FlowActionType actionType = flowAction.getType();
			if (actionType != FlowActionType.CANCEL) {
				fieldsInError = validateAndPersist(formFlow, actionName, actionType, parameterMap);
			}
		}

		if (fieldsInError == null || fieldsInError.isEmpty()) {
			// Find next form
			if (actionName != null) {
				String nextUrl = formFlow.doAction(actionName, actionParams, documentHelper);
				formSubmissionResult.setNextUrl(nextUrl);
			}
		} else {
			throw new FlowExceptionBadRequest("Validation error.");
		}
		return formSubmissionResult;
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
	
	public Set<String> validateAndPersist(FormFlow formFlow, String actionName, FlowActionType actionType, Map<String, String> parameterMap) throws IOException, FlowExceptionFileNotFound, FlowExceptionJavaScript, FlowExceptionXPath {

		// Collect input values
		List<InputPojo> inputPojos = formFlow.getCurrentInputPojos();
		for (InputPojo inputPojo : inputPojos) {
			if (inputPojo.getType().equalsIgnoreCase("checkbox")) {
				inputPojo.setValue(parameterMap.get(inputPojo.getName()) != null ? "true" : "false");
			} else {
				String value = parameterMap.get(inputPojo.getName());
				if (value == null) {
					value = "";
				}
				inputPojo.setValue(value);
			}
		}
		
		Scriptable workingScope = masterScope.createWorkingScope(formFlow.getLibraries());
		
		// Process includeIf fields
		List<InputPojo> includeFalseInputs = getIncludeFalseInputs(inputPojos, workingScope);
		inputPojos.removeAll(includeFalseInputs);

		// Process calculated fields
		processCalculatedFields(inputPojos, workingScope);
		
		// Validate fields
		Set<String> fieldsInError = validateInput(inputPojos, actionName, workingScope);

		// If Back action remove any invalid input
		if (actionType == FlowActionType.BACK && !fieldsInError.isEmpty()) {
			HashSet<InputPojo> inputPOJOsToRemove = new HashSet<InputPojo>();
			for (InputPojo inputPojo : inputPojos) {
				if (fieldsInError.contains(inputPojo.getName())) {
					inputPOJOsToRemove.add(inputPojo);
				}
			}
			inputPojos.removeAll(inputPOJOsToRemove);
			fieldsInError.clear();
		}

		if (fieldsInError.isEmpty()) {
			// Persist collected form data
			String docBase = formFlow.getCurrentDocBase();
			documentHelper.persistFormData(inputPojos, docBase, formFlow.getDataDocument());
			documentHelper.clearFormData(includeFalseInputs, docBase, formFlow.getDataDocument());
		}

		return fieldsInError;
	}

	List<InputPojo> getIncludeFalseInputs(List<InputPojo> inputPojos, Scriptable workingScope) {
		List<InputPojo> includeFalseInputPojos = new ArrayList<InputPojo>();
		List<InputPojo> inputsWithIncludeIfStatements = new ArrayList<InputPojo>();
		for (InputPojo inputPojo : inputPojos) {
			if (inputPojo.getRfAttributes().containsKey(Constants.INCLUDE_IF_ATTR)) {
				inputsWithIncludeIfStatements.add(inputPojo);
			}
		}
		if (!inputsWithIncludeIfStatements.isEmpty()) {
			Context context = Context.getCurrentContext();
			addFieldsToScope(inputPojos, workingScope, context);
			for (InputPojo inputPojo : inputsWithIncludeIfStatements) {
				String inputName = inputPojo.getName();
				String includeIfStatement = inputPojo.getRfAttributes().get(Constants.INCLUDE_IF_ATTR);
				Object object = context.evaluateString(workingScope, includeIfStatement, inputName + " " + Constants.INCLUDE_IF_ATTR +" statement", 1, null);
				boolean b = Context.toBoolean(object);
				logger.debug("input {} name:'{}', result:'{}'", new Object[] {Constants.INCLUDE_IF_ATTR, inputName, b});
				if (!b) {
					includeFalseInputPojos.add(inputPojo);
				}
			}
		}
		return includeFalseInputPojos;
	}

	void processCalculatedFields(List<InputPojo> inputPojos, Scriptable workingScope) {
		List<InputPojo> inputsWithCalculatedStatements = new ArrayList<InputPojo>();
		for (InputPojo inputPojo : inputPojos) {
			if (inputPojo.getRfAttributes().containsKey(Constants.CALCULATED_ATTR)) {
				inputsWithCalculatedStatements.add(inputPojo);
			}
		}
		if (!inputsWithCalculatedStatements.isEmpty()) {
			Context context = Context.getCurrentContext();
			addFieldsToScope(inputPojos, workingScope, context);
			for (InputPojo inputPojo : inputsWithCalculatedStatements) {
				String inputName = inputPojo.getName();
				String calcExpression = inputPojo.getRfAttributes().get(Constants.CALCULATED_ATTR);
				Object result = context.evaluateString(workingScope, calcExpression, inputName + " " + Constants.CALCULATED_ATTR + " statement", 1, null);
				String resultString = Context.toString(result);
				logger.debug("input {} name:'{}', result:'{}'", new Object[] {Constants.CALCULATED_ATTR, inputName, resultString});
				inputPojo.setValue(resultString);
			}
		}
	}

	private void addFieldsToScope(List<InputPojo> inputPojos, Scriptable workingScope, Context context) {
		String jsPojoMapString = jsSerialiser.inputPOJOListToJS(inputPojos);
		context.evaluateString(workingScope, "var fields = " + jsPojoMapString, "Add fields to scope", 1, null);
	}
	
	Set<String> validateInput(List<InputPojo> inputPojos, String actionName, Scriptable workingScope) throws FlowExceptionJavaScript {
		Set<String> fieldsInError = new HashSet<String>();
		String jsPojoMapString = jsSerialiser.inputPOJOListToJS(inputPojos);
		logger.debug("inputPojos as js:{}", jsPojoMapString);
		StringBuilder commandStringBuilder = new StringBuilder();
		commandStringBuilder.append("rf.validateFields(");
		commandStringBuilder.append(jsPojoMapString);
		commandStringBuilder.append(", '");
		commandStringBuilder.append(actionName);
		commandStringBuilder.append("')");
		Context jsContext = Context.getCurrentContext();
		try {
			String string = commandStringBuilder.toString();
			System.out.println(string);
			NativeArray errors = (NativeArray) jsContext.evaluateString(workingScope, string, "<cmd>", 1, null);
			for (int i = 0; i < errors.getLength(); i++) {
				ScriptableObject error = (ScriptableObject) errors.get(i, workingScope);
				String errorFieldName = error.get("name", workingScope).toString();
				String errorFieldMessage = error.get("message", workingScope).toString();
				logger.info("Input error, name:'{}', message:'{}'", errorFieldName, errorFieldMessage);
				fieldsInError.add(errorFieldName);
			}
		} catch (EcmaError e) {
			throw new FlowExceptionJavaScript("EcmaError error while calling JavaScript function rf.validateFields.", e);
		} catch (WrappedException e) {
			throw new FlowExceptionJavaScript("WrappedException error while calling JavaScript function rf.validateFields.", e);
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
						paramValue = URLDecoder.decode(paramValue, Constants.UTF8);
					} catch (UnsupportedEncodingException e) {
						logger.warn("UnsupportedEncodingException while decoding paramValue:'{}', using " + Constants.UTF8, paramValue, e);
					}
					paramsMap.put(paramName, paramValue);
				}
			}
		}
		return paramsMap;
	}
	
}
