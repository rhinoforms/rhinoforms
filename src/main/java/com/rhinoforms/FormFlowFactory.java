package com.rhinoforms;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.w3c.dom.Document;

import com.rhinoforms.resourceloader.ResourceLoader;

public class FormFlowFactory {

	private ResourceLoader resourceLoader;
	private JSMasterScope masterScope;
	private DocumentHelper documentHelper;
	private RemoteSubmissionHelper remoteSubmissionHelper;

	public FormFlowFactory(ResourceLoader resourceLoader, JSMasterScope masterScope, String contextPath) {
		this.resourceLoader = resourceLoader;
		this.masterScope = masterScope;
		this.documentHelper = new DocumentHelper();
		this.remoteSubmissionHelper = new RemoteSubmissionHelper(resourceLoader, contextPath);
	}

	public FormFlow createFlow(String formFlowPath, String dataDocumentString) throws IOException,
			FormFlowFactoryException {

		try {
			Scriptable scope = masterScope.createWorkingScope();
			
			// Create flow
			FormFlow formFlow = new FormFlow(remoteSubmissionHelper);
			formFlow.setResourcesBase(resolveResourcesBase(formFlowPath));
			loadFlowFromJSDefinition(formFlow, formFlowPath, scope, masterScope.getCurrentContext());

			// Load or create data document
			String flowDocBase = formFlow.getFlowDocBase();
			if (flowDocBase != null) {
				String defaultInitalData = formFlow.getDefaultInitialData();

				// Parse or create initial document. Make sure flow docBase node is there.
				Document dataDocument = null;
				if (dataDocumentString != null && !dataDocumentString.isEmpty()) {
					try {
						dataDocument = documentHelper.streamToDocument(new ByteArrayInputStream(dataDocumentString.getBytes()));
					} catch (DocumentHelperException e) {
						throw new FormFlowFactoryException("Error parsing given initial data document", e);
					}
				} else {
					if (defaultInitalData != null) {
						try {
							dataDocument = documentHelper.streamToDocument(resourceLoader.getResourceAsStream(formFlow.getDefaultInitialData()));
						} catch (DocumentHelperException e) {
							throw new FormFlowFactoryException("Error parsing default initial data document", e);
						}
					} else {
						dataDocument = documentHelper.newDocument();
					}
				}

				documentHelper.createNodeIfNotThere(dataDocument, flowDocBase);

				formFlow.setDataDocument(dataDocument);

				return formFlow;
			} else {
				throw new FormFlowFactoryException("Please specify a form-flow docBase.");
			}
		} catch (EvaluatorException e) {
			throw new FormFlowFactoryException("Error parsing flow js file.", e);
		} catch (DocumentHelperException e) {
			throw new FormFlowFactoryException("Error creating base node in data document using flow docBase.", e);
		}
	}

	private String resolveResourcesBase(String formFlowPath) {
		String resourcesBase = "";
		if (formFlowPath.contains("/")) {
			resourcesBase = formFlowPath.substring(0, formFlowPath.lastIndexOf("/") + 1);
		}
		if (!resourcesBase.isEmpty() && resourcesBase.charAt(0) != '/') {
			resourcesBase = '/' + resourcesBase;
		}
		return resourcesBase;
	}

	private void loadFlowFromJSDefinition(FormFlow formFlow, String formFlowJSDefinitionPath, Scriptable scope, Context jsContext)
			throws IOException, FileNotFoundException {
		Object wrappedFormFlow = Context.javaToJS(formFlow, scope);
		ScriptableObject.putProperty(scope, "formFlow", wrappedFormFlow);
		String scriptPath = "/flow-loader.js";
		jsContext.evaluateReader(scope, new InputStreamReader(FormFlowFactory.class.getResourceAsStream(scriptPath)), scriptPath, 1,
				null);

		InputStreamReader inputStreamReader = new InputStreamReader(resourceLoader.getResourceAsStream(formFlowJSDefinitionPath));
		BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("loadFlow(");
		while (bufferedReader.ready()) {
			stringBuilder.append(bufferedReader.readLine());
		}
		stringBuilder.append(")");

		String newFlowJsExpresion = stringBuilder.toString();
		jsContext.evaluateString(scope, newFlowJsExpresion, formFlowJSDefinitionPath, 1, null);
		
		List<String> libraries = formFlow.getLibraries();
		for (int a = 0; a < libraries.size(); a++) {
			libraries.set(a, formFlow.resolveResourcePathIfRelative(libraries.get(a)));
		}
		
		if (formFlow.getDefaultInitialData() != null) {
			formFlow.setDefaultInitialData(formFlow.resolveResourcePathIfRelative(formFlow.getDefaultInitialData()));
		}
	}

}
