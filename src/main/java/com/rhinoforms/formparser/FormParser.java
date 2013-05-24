package com.rhinoforms.formparser;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

import org.htmlcleaner.ContentNode;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.HtmlNode;
import org.htmlcleaner.SimpleHtmlSerializer;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.rhinoforms.Constants;
import com.rhinoforms.RhinoformsProperties;
import com.rhinoforms.flow.FieldSourceProxy;
import com.rhinoforms.flow.FlowAction;
import com.rhinoforms.flow.FlowActionType;
import com.rhinoforms.flow.FormFlow;
import com.rhinoforms.flow.InputPojo;
import com.rhinoforms.flow.ProxyFactory;
import com.rhinoforms.flow.SubmissionTimeKeeper;
import com.rhinoforms.js.JSMasterScope;
import com.rhinoforms.resourceloader.ResourceLoader;
import com.rhinoforms.resourceloader.ResourceLoaderException;
import com.rhinoforms.util.StreamUtils;

public class FormParser {

	private ResourceLoader resourceLoader;
	private SelectOptionHelper selectOptionHelper;
	private ProxyFactory proxyFactory;
	private ValueInjector valueInjector;
	private HtmlCleaner htmlCleaner;
	private boolean showDebugBar;
	private TagNode debugBarNode;
	private SubmissionTimeKeeper submissionTimeKeeper;
	private final Logger logger = LoggerFactory.getLogger(FormParser.class);

	private static final String INPUT = "input";
	private static final String SELECT = "select";
	private static final String TEXTAREA = "textarea";
	private static final String CHECKBOX = "checkbox";
	private static final String RADIO = "radio";
	private static final FieldPathHelper fieldPathHelper = new FieldPathHelper();
	private static final int processIncludesMaxDepth = 10;

	public FormParser(HtmlCleaner htmlCleaner, ValueInjector valueInjector, ResourceLoader resourceLoader, SubmissionTimeKeeper submissionTimeKeeper) {
		this.resourceLoader = resourceLoader;
		this.submissionTimeKeeper = submissionTimeKeeper;
		this.selectOptionHelper = new SelectOptionHelper(resourceLoader);
		this.proxyFactory = new ProxyFactory();
		this.valueInjector = valueInjector;
		this.htmlCleaner = htmlCleaner;
		showDebugBar = RhinoformsProperties.getInstance().isShowDebugBar();
		debugBarNode = loadDebugBar();
	}

	public void parseForm(InputStream formStream, FormFlow formFlow, PrintWriter writer, JSMasterScope masterScope, boolean suppressDebugBar)
			throws FormParserException {

		try {
			TagNode formHtml = htmlCleaner.clean(formStream);
			String flowID = formFlow.getId();
	
			Document dataDocument = formFlow.getDataDocument();
			String docBase = formFlow.getCurrentDocBase();
			String currentPath = formFlow.getCurrentPath();
			String formId = formFlow.getCurrentFormId();
			Map<String, FlowAction> currentActions = formFlow.getCurrentActions();
	
			// Process rf.include
			processIncludes(formHtml, formFlow);
	
			// Add debugBar
			if (showDebugBar && !suppressDebugBar) {
				addDebugBar(formHtml);
			}
	
			// Process rf.forEach statements
			valueInjector.processForEachStatements(formFlow.getProperties(), formHtml, dataDocument, docBase);
			
			valueInjector.processCurlyBrackets(dataDocument, formHtml, formFlow.getProperties(), docBase);
	
			// Process first Rhinoforms form in doc
			Object[] rfFormNodes = formHtml.evaluateXPath("//form[@" + Constants.RHINOFORMS_FLAG + "='true']");
			if (rfFormNodes.length > 0) {
				logger.debug("{} forms found.", rfFormNodes.length);
				TagNode formNode = (TagNode) rfFormNodes[0];
				
				perpetuateIncludeIfStatementsToInputs(formHtml);
	
				// Process dynamic select elements
				processSelectSource(formNode, formFlow);
	
				// Process range select elements
				processSelectRange(formNode, masterScope);
	
				// Record input fields
				recordInputFieldsPushInValues(formNode, formFlow, dataDocument, docBase);
	
				// Process Actions
				processActions(currentActions, formNode, formFlow.getCurrentFormId());
	
				// Process auto-complete fields, replace source with proxy path
				processInputSourceFields(formNode, currentPath, formFlow);
	
				// Add flowId as hidden field
				addFlowId(flowID, formNode);
	
				// Mark form as parsed
				formNode.setAttribute("parsed", "true");
				
				// Add the form id as a class on the form
				formNode.setAttribute(Constants.CLASS, addClass(formNode.getAttributeByName(Constants.CLASS), formId));
			} else {
				logger.warn("No forms found");
			}
	
			// Write out processed document
			new SimpleHtmlSerializer(htmlCleaner.getProperties()).write(formHtml, writer, "utf-8");
		} catch (IOException e) {
			throw new FormParserException(e);
		} catch (XPatherException e) {
			throw new FormParserException(e);
		} catch (ResourceLoaderException e) {
			throw new FormParserException(e);
		} catch (XPathExpressionException e) {
			throw new FormParserException(e);
		} catch (ValueInjectorException e) {
			throw new FormParserException(e);
		}
	}

	void processIncludes(TagNode html, FormFlow formFlow) throws IOException, FormParserException {
		doProcessIncludes(html, 0, formFlow);
	}

	private void doProcessIncludes(TagNode html, int depth, FormFlow formFlow) throws IOException, FormParserException {
		if (depth < processIncludesMaxDepth) {
			@SuppressWarnings("unchecked")
			List<TagNode> includeNodes = html.getElementListByName(Constants.INCLUDE_ELEMENT, true);
			for (TagNode includeNode : includeNodes) {
				String srcAttribute = includeNode.getAttributeByName("src");
				srcAttribute = formFlow.resolveResourcePathIfRelative(srcAttribute);
				logger.debug("Processing include. Resolved resource path '{}'", srcAttribute);
				InputStream resourceAsStream = resourceLoader.getFormResourceAsStream(srcAttribute);
				if (resourceAsStream != null) {
					TagNode includeHtml = htmlCleaner.clean(resourceAsStream);
					TagNode body = includeHtml.findElementByName("body", false);
					doProcessIncludes(body, depth + 1, formFlow);
	
					@SuppressWarnings("unchecked")
					List<HtmlNode> bodyChildren = body.getChildren();
					Collections.reverse(bodyChildren);
					TagNode includeParent = includeNode.getParent();
					for (HtmlNode bodyChild : bodyChildren) {
						// Having to call addChild and then removeChild because insertChildAfter does not seem to set the new child's 'parent' node.
						includeParent.addChild(bodyChild);
						includeParent.removeChild(bodyChild);
						includeParent.insertChildAfter(includeNode, bodyChild);
					}
					includeParent.removeChild(includeNode);
				} else {
					throw new FormParserException("Include file not found. Path:'" + srcAttribute + "'");
				}
			}
		} else {
			throw new FormParserException("Exceeded maximum nested " + Constants.INCLUDE_ELEMENT + " depth of " + processIncludesMaxDepth);
		}
	}

	private void perpetuateIncludeIfStatementsToInputs(TagNode formHtml) throws XPatherException {
		Object[] includeIfNodes = formHtml.evaluateXPath("//*[@" + Constants.INCLUDE_IF_ATTR + "]");
		for (Object includeIfNodeO : includeIfNodes) {
			TagNode includeIfNode = (TagNode) includeIfNodeO;
			String name = includeIfNode.getName();
			if (!name.equals(INPUT) && !name.equals(SELECT) && !name.equals(TEXTAREA)) {
				String parentIncludeIf = includeIfNode.getAttributeByName(Constants.INCLUDE_IF_ATTR);

				@SuppressWarnings("unchecked")
				List<TagNode> inputs = includeIfNode.getElementListByName(INPUT, true);
				@SuppressWarnings("unchecked")
				List<TagNode> selects = includeIfNode.getElementListByName(SELECT, true);
				inputs.addAll(selects);
				@SuppressWarnings("unchecked")
				List<TagNode> textareas = includeIfNode.getElementListByName(TEXTAREA, true);
				inputs.addAll(textareas);
				for (TagNode inputTagNode : inputs) {
					String inputIncludeIf = inputTagNode.getAttributeByName(Constants.INCLUDE_IF_ATTR);
					if (inputIncludeIf == null) {
						inputTagNode.setAttribute(Constants.INCLUDE_IF_ATTR, parentIncludeIf);
					}
				}
			}
		}
	}

	private void processSelectSource(TagNode formNode, FormFlow formFlow) throws XPatherException, ResourceLoaderException {
		Object[] dynamicSelectNodes = formNode.evaluateXPath("//select[@" + Constants.SELECT_SOURCE_ATTR + "]");
		for (Object dynamicSelectNodeO : dynamicSelectNodes) {
			TagNode dynamicSelectNode = (TagNode) dynamicSelectNodeO;
			String name = dynamicSelectNode.getAttributeByName(Constants.NAME_ATTR);
			String source = dynamicSelectNode.getAttributeByName(Constants.SELECT_SOURCE_ATTR);
			source = formFlow.resolveResourcePathIfRelative(source);
			String preselectFirstOption = dynamicSelectNode.getAttributeByName(Constants.SELECT_PRESELECT_FIRST_OPTION_ATTR);
			dynamicSelectNode.removeAttribute(Constants.SELECT_SOURCE_ATTR);
			dynamicSelectNode.removeAttribute(Constants.SELECT_PRESELECT_FIRST_OPTION_ATTR);
			logger.debug("Found dynamicSelectNode name:{}, source:{}", name, source);
	
			List<SelectOptionPojo> options = selectOptionHelper.loadOptions(source);
			if (!"true".equals(preselectFirstOption)) {
				options.add(0, new SelectOptionPojo("-- Please Select --", ""));
			}
			for (SelectOptionPojo selectOptionPojo : options) {
				TagNode optionNode = new TagNode("option");
				String value = selectOptionPojo.getValue();
				if (value != null) {
					optionNode.setAttribute("value", value);
				}
				optionNode.addChild(new ContentNode(selectOptionPojo.getText()));
				dynamicSelectNode.addChild(optionNode);
			}
		} // TODO: validate that submitted value comes from the list
	}

	private void processInputSourceFields(TagNode formNode, String currentPath, FormFlow formFlow) throws XPatherException {
		Object[] autoCompleteNodes = formNode.evaluateXPath("//input[@" + Constants.SELECT_SOURCE_ATTR + "]");
		for (Object autoCompleteNodeO : autoCompleteNodes) {
			TagNode autoCompleteNode = (TagNode) autoCompleteNodeO;
			String fieldName = autoCompleteNode.getAttributeByName(Constants.NAME_ATTR);
			String source = autoCompleteNode.getAttributeByName(Constants.INPUT_SOURCE_ATTR);

			FieldSourceProxy fieldSourceProxy = proxyFactory.createFlowProxy(currentPath, fieldName, source);
			formFlow.addFieldSourceProxy(fieldSourceProxy);
			autoCompleteNode.removeAttribute(Constants.INPUT_SOURCE_ATTR);
			autoCompleteNode.setAttribute("rf.source", "rhinoforms/proxy/" + fieldSourceProxy.getProxyPath());
		}
	}

	private void addFlowId(String flowID, TagNode formNode) {
		TagNode flowIdNode = new TagNode(INPUT);
		flowIdNode.setAttribute("name", Constants.FLOW_ID_FIELD_NAME);
		flowIdNode.setAttribute("type", "hidden");
		flowIdNode.setAttribute("value", flowID + "");
		formNode.insertChild(0, flowIdNode);
	}

	private void processActions(Map<String, FlowAction> currentActions, TagNode formNode, String formId) {
		@SuppressWarnings("unchecked")
		List<TagNode> actions = formNode.getElementListHavingAttribute(Constants.ACTION_ATTR, true);
		for (TagNode actionTagNode : actions) {
			String actionName = actionTagNode.getAttributeByName(Constants.ACTION_ATTR);
			FlowAction flowAction = currentActions.get(actionName);
			if (flowAction != null) {
				FlowActionType type = flowAction.getType();
				if (type != null) {
					actionTagNode.setAttribute(Constants.ACTION_TYPE_ATTR, type.toString());
					List<Integer> estimate = submissionTimeKeeper.getEstimate(formId, actionName);
					if (estimate != null) {
						actionTagNode.setAttribute(Constants.ACTION_TIME_ESTIMATE_ATTR, estimate.toString());
					}
				}
			}
		}
	}

	private void recordInputFieldsPushInValues(TagNode formNode, FormFlow formFlow, Document dataDocument, String docBase)
			throws XPathExpressionException, XPatherException {
		List<InputPojo> inputPojos = new ArrayList<InputPojo>();
		Map<String, InputPojo> inputPojosMap = new HashMap<String, InputPojo>();

		@SuppressWarnings("unchecked")
		List<TagNode> inputs = formNode.getElementListByName(INPUT, true);
		@SuppressWarnings("unchecked")
		List<TagNode> selects = formNode.getElementListByName(SELECT, true);
		inputs.addAll(selects);
		@SuppressWarnings("unchecked")
		List<TagNode> textareas = formNode.getElementListByName(TEXTAREA, true);
		inputs.addAll(textareas);
		for (TagNode inputTagNode : inputs) {
			String name = inputTagNode.getAttributeByName(Constants.NAME_ATTR);
			if (name != null) {
				String type;

				if (inputTagNode.getName().equals(SELECT)) {
					type = SELECT;
				} else if (inputTagNode.getName().equals(TEXTAREA)) {
					type = TEXTAREA;
				} else {
					type = inputTagNode.getAttributeByName(Constants.TYPE_ATTR);
				}

				if (type != null) {

					if (!(type.equals(RADIO) && inputPojosMap.containsKey(name))) {

						// Collect all rf.xxx attributes
						Map<String, String> rfAttributes = new HashMap<String, String>();
						Map<String, String> attributes = inputTagNode.getAttributes();
						for (String attName : attributes.keySet()) {
							if (attName.startsWith("rf.")) {
								rfAttributes.put(attName, attributes.get(attName));
							}
						}

						InputPojo inputPojo = new InputPojo(name, type, rfAttributes);
						inputPojosMap.put(name, inputPojo);
						inputPojos.add(inputPojo);
					}

					// Push values from the dataDocument into the form html.
					String inputValue = lookupValueByFieldName(dataDocument, name, docBase);
					if (inputValue != null) {
						if (type.equals(RADIO)) {
							String value = inputTagNode.getAttributeByName(Constants.VALUE_ATTR);
							if (inputValue.equals(value)) {
								inputTagNode.setAttribute(Constants.CHECKED_ATTR, Constants.CHECKED_ATTR);
							}
						} else if (type.equals(CHECKBOX)) {
							if (inputValue.equals("true")) {
								inputTagNode.setAttribute(Constants.CHECKED_ATTR, Constants.CHECKED_ATTR);
							}
						} else if (type.equals(SELECT)) {
							Object[] nodes = inputTagNode.evaluateXPath("option[@value=\"" + inputValue + "\"]");
							if (nodes.length == 0) {
								nodes = inputTagNode.evaluateXPath("option[text()=\"" + inputValue + "\"]");
							}
							if (nodes.length > 0) {
								((TagNode) nodes[0]).setAttribute(Constants.SELECTED_ATTR, "selected");
							}
						} else if (type.equals(TEXTAREA)) {
							inputTagNode.addChild(new ContentNode(inputValue));
						} else {
							inputTagNode.setAttribute("value", inputValue);
						}
					}
				} else {
					logger.debug("Input name:{} has no type attribute!", name);
				}
			}
		}
		formFlow.setCurrentInputPojos(inputPojos);
	}

	private void processSelectRange(TagNode formNode, JSMasterScope masterScope) throws XPatherException, IOException {
		Object[] rangeSelectNodes = formNode.evaluateXPath("//select[@" + Constants.SELECT_RANGE_START_ATTR + "]");
		if (rangeSelectNodes.length > 0) {
			Scriptable workingScope = masterScope.createWorkingScope();
			Context context = masterScope.getCurrentContext();
			for (Object rangeSelectNodeO : rangeSelectNodes) {
				TagNode rangeSelectNode = (TagNode) rangeSelectNodeO;
				String name = rangeSelectNode.getAttributeByName(Constants.NAME_ATTR);
				String rangeStart = rangeSelectNode.getAttributeByName(Constants.SELECT_RANGE_START_ATTR);
				String rangeEnd = rangeSelectNode.getAttributeByName(Constants.SELECT_RANGE_END_ATTR);
				String preselectFirstOption = rangeSelectNode.getAttributeByName(Constants.SELECT_PRESELECT_FIRST_OPTION_ATTR);
				rangeSelectNode.removeAttribute(Constants.SELECT_RANGE_START_ATTR);
				rangeSelectNode.removeAttribute(Constants.SELECT_RANGE_END_ATTR);
				rangeSelectNode.removeAttribute(Constants.SELECT_PRESELECT_FIRST_OPTION_ATTR);

				logger.debug("Found rangeSelectNode name:{}, rangeStart:{}, rangeEnd:{}", new String[] { name, rangeStart, rangeEnd });
				boolean rangeStartValid = rangeStart != null && !rangeStart.isEmpty();
				boolean rangeEndValid = rangeEnd != null && !rangeEnd.isEmpty();
				if (rangeStartValid && rangeEndValid) {
					Object rangeStartResult = context.evaluateString(workingScope, "{" + rangeStart + "}",
							Constants.SELECT_RANGE_START_ATTR, 1, null);
					Object rangeEndResult = context.evaluateString(workingScope, "{" + rangeEnd + "}", Constants.SELECT_RANGE_END_ATTR,
							1, null);
					logger.debug("RangeSelectNode name:{}, rangeStartResult:{}, rangeEndResult:{}", new Object[] { name,
							rangeStartResult, rangeEndResult });

					double rangeStartResultNumber = Context.toNumber(rangeStartResult);
					double rangeEndResultNumber = Context.toNumber(rangeEndResult);
					String comparator;
					String incrementor;
					if (rangeStartResultNumber < rangeEndResultNumber) {
						comparator = "<=";
						incrementor = "++";
					} else {
						comparator = ">=";
						incrementor = "--";
					}

					String rangeStatement = "{ var range = []; for( var i = " + rangeStartResult + "; i " + comparator + " "
							+ rangeEndResult + "; i" + incrementor + ") { range.push(i); }; '' + range; }";
					logger.debug("RangeSelectNode name:{}, rangeStatement:{}", name, rangeStatement);
					String rangeResult = (String) context.evaluateString(workingScope, rangeStatement, "Calculate range", 1, null);
					logger.debug("RangeSelectNode name:{}, rangeResult:{}", name, rangeResult);

					if (!"true".equals(preselectFirstOption)) {
						TagNode optionNode = new TagNode("option");
						optionNode.setAttribute("value", "");
						optionNode.addChild(new ContentNode("-- Please Select --"));
						rangeSelectNode.addChild(optionNode);
					}

					for (String item : rangeResult.split(",")) {
						TagNode optionNode = new TagNode("option");
						optionNode.addChild(new ContentNode(item));
						rangeSelectNode.addChild(optionNode);
					}

				} else {
					logger.warn("Range select node '{}' not processed because {} is empty.", name,
							(rangeStartValid ? Constants.SELECT_RANGE_START_ATTR : Constants.SELECT_RANGE_END_ATTR));
				}
			}
		}
	}

	private void addDebugBar(TagNode formHtml) {
		TagNode body = formHtml.findElementByName("body", false);
		int size = body.getChildren().size();
		body.insertChild(size, debugBarNode);
	}

	String lookupValueByFieldName(Node document, String name, String docBase) throws XPathExpressionException {
		String inputValue = null;
		XPathExpression xPathExpression = fieldPathHelper.fieldToXPathExpression(docBase, name);
		NodeList nodeList = (NodeList) xPathExpression.evaluate(document, XPathConstants.NODESET);
		if (nodeList != null && nodeList.getLength() == 1) {
			inputValue = nodeList.item(0).getTextContent();
		} else if (nodeList != null && nodeList.getLength() > 1) {
			logger.warn("Multiple nodes matched for documentBasePath: '{}', field name: '{}"
					+ "'. No value will be pushed into the form and there may be submission problems.", docBase, name);
		}
		return inputValue;
	}

	private TagNode loadDebugBar() {
		try {
			InputStream debugBarStream = FormParser.class.getResourceAsStream("/debugbar.html");
			String barHtmlString = new String(new StreamUtils().readStream(debugBarStream));
			barHtmlString = barHtmlString.replace("{viewDataDocumentUrl}", "");

			TagNode html = htmlCleaner.clean(barHtmlString);
			TagNode body = (TagNode) html.getChildren().get(1);
			TagNode div = (TagNode) body.getChildren().get(0);
			return div;
		} catch (IOException e) {
			RuntimeException runtimeException = new RuntimeException("Failed to load debugBar.", e);
			logger.error(runtimeException.getMessage(), runtimeException);
			throw runtimeException;
		}
	}

	private String addClass(String existingClassString, String classToAdd) {
		if (existingClassString == null) {
			existingClassString = "";
		} else if (!existingClassString.isEmpty()) {
			existingClassString += " ";
		}
		existingClassString += classToAdd;
		return existingClassString;
	}
	
	public void setShowDebugBar(boolean showDebugBar) {
		this.showDebugBar = showDebugBar;
	}
	
}
