package com.rhinoforms;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

public class FormFlowFactory {

	public FormFlow createFlow(String realFormFlowPath, Context jsContext) throws IOException {
		ScriptableObject scope = jsContext.initStandardObjects();
		
		FormFlow formFlow = new FormFlow(scope);
		HashMap<String, List<Form>> formLists = new HashMap<String, List<Form>>();
		Object wrappedFormLists = Context.javaToJS(formLists, scope);
		ScriptableObject.putProperty(scope, "formLists", wrappedFormLists);
		String scriptPath = "/flow-loader.js";
		jsContext.evaluateReader(scope, new InputStreamReader(FormFlowFactory.class.getResourceAsStream(scriptPath)), scriptPath, 1, null);
		
		InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(realFormFlowPath));
		BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("loadFlow(");
		while (bufferedReader.ready()) {
			stringBuilder.append(bufferedReader.readLine());
		}
		stringBuilder.append(")");
		
		String newFlowJsExpresion = stringBuilder.toString();
		jsContext.evaluateString(scope, newFlowJsExpresion, realFormFlowPath, 1, null);
		formFlow.setFormLists(formLists);
		return formFlow;
	}
	
}
