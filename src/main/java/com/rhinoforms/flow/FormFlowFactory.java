package com.rhinoforms.flow;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.rhinoforms.formparser.ValueInjector;
import com.rhinoforms.js.JSMasterScope;
import com.rhinoforms.resourceloader.ResourceLoader;
import com.rhinoforms.xml.DocumentHelper;
import com.rhinoforms.xml.DocumentHelperException;

public class FormFlowFactory {

	private ResourceLoader resourceLoader;
	private JSMasterScope masterScope;
	private String servletContextPath;
	private DocumentHelper documentHelper;
	private ValueInjector valueInjector;
	private SubmissionTimeKeeper submissionTimeKeeper;
	private static final Logger LOGGER = LoggerFactory.getLogger(FormFlowFactory.class);

	public FormFlowFactory(ResourceLoader resourceLoader, JSMasterScope masterScope, String servletContextPath, SubmissionTimeKeeper submissionTimeKeeper) {
		this.resourceLoader = resourceLoader;
		this.masterScope = masterScope;
		this.servletContextPath = servletContextPath;
		this.submissionTimeKeeper = submissionTimeKeeper;
		this.documentHelper = new DocumentHelper();
		this.valueInjector = new ValueInjector();
	}

	public FormFlow createFlow(String formFlowPath, String dataDocumentString) throws IOException,
			FormFlowFactoryException {

		try {
			Scriptable scope = masterScope.createWorkingScope();
			
			// Create flow
			FormFlow formFlow = new FormFlow();
			formFlow.setResourcesBase(resolveResourcesBase(formFlowPath));
			formFlow.setSubmissionTimeKeeper(submissionTimeKeeper);
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
							dataDocument = documentHelper.streamToDocument(resourceLoader.getFormResourceAsStream(formFlow.getDefaultInitialData()));
						} catch (DocumentHelperException e) {
							throw new FormFlowFactoryException("Error parsing default initial data document", e);
						}
					} else {
						dataDocument = documentHelper.newDocument();
					}
				}
				
				documentHelper.createNodeIfNotThere(dataDocument, flowDocBase);
				LOGGER.debug("DataDocument first child {}", dataDocument.getFirstChild());

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
			throws IOException, FileNotFoundException, FormFlowFactoryException {
		
		Properties flowProperties = loadFlowProperties(formFlowJSDefinitionPath);
		if (flowProperties != null) {
			flowProperties.put("contextPath", servletContextPath);
			fillPlaceholders(flowProperties);
			formFlow.setProperties(flowProperties);
		}
		
		Object wrappedFormFlow = Context.javaToJS(formFlow, scope);
		ScriptableObject.putProperty(scope, "formFlow", wrappedFormFlow);
		String scriptPath = "/flow-loader.js";
		jsContext.evaluateReader(scope, new InputStreamReader(FormFlowFactory.class.getResourceAsStream(scriptPath)), scriptPath, 1,
				null);

		InputStreamReader inputStreamReader = new InputStreamReader(resourceLoader.getFormResourceAsStream(formFlowJSDefinitionPath));
		BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
		StringBuilder flowStringBuilder = new StringBuilder();
		while (bufferedReader.ready()) {
			flowStringBuilder.append(bufferedReader.readLine());
		}

		valueInjector.processFlowDefinitionCurlyBrackets(flowStringBuilder, flowProperties);
		
		flowStringBuilder.insert(0, "loadFlow(");
		flowStringBuilder.append(")");
		
		String newFlowJsExpresion = flowStringBuilder.toString();
		jsContext.evaluateString(scope, newFlowJsExpresion, formFlowJSDefinitionPath, 1, null);
		
		List<String> libraries = formFlow.getLibraries();
		for (int a = 0; a < libraries.size(); a++) {
			libraries.set(a, formFlow.resolveResourcePathIfRelative(libraries.get(a)));
		}
		
		if (formFlow.getDefaultInitialData() != null) {
			formFlow.setDefaultInitialData(formFlow.resolveResourcePathIfRelative(formFlow.getDefaultInitialData()));
		}
	}
	
	private Properties loadFlowProperties(String formFlowJSDefinitionPath) throws IOException {
		String flowPropertiesPath = formFlowJSDefinitionPath.replace(".js", ".properties");
		try {
			InputStream formPropertiesStream = resourceLoader.getFormResourceAsStream(flowPropertiesPath);
			if (formPropertiesStream != null) {
				Properties properties = new Properties();
				properties.load(formPropertiesStream);
				return properties;
			}
		} catch (FileNotFoundException e) {
			// No problem, properties file is optional
		}
		return null;
	}
	
	public void fillPlaceholders(Properties properties) {
		Pattern propertyNamePlaceholderPattern = Pattern.compile("\\{([a-zA-Z0-9_-]+)\\}");
		
		for (Object object : properties.keySet()) {
			String key = (String) object;
			String value = properties.getProperty(key);
			Matcher matcher = propertyNamePlaceholderPattern.matcher(value);
			while (matcher.find()) {
				String group = matcher.group(1);
				if (properties.containsKey(group)) {
					value = replaceGroup(value, group, (String) properties.get(group));
				}
			}
			properties.setProperty(key, value);
		}
		
	}
	
	private String replaceGroup(String value, String group, String newValue) {
		return value.replace("{" + group + "}", newValue);
	}
	
}
