package com.rhinoforms;

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

import org.htmlcleaner.CleanerProperties;
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

import com.rhinoforms.resourceloader.ResourceLoader;
import com.rhinoforms.resourceloader.ResourceLoaderException;
import com.rhinoforms.serverside.InputPojo;
import com.rhinoforms.util.StreamUtils;

public class FormParser {

	private ResourceLoader resourceLoader;
	private SelectOptionHelper selectOptionHelper;
	private ProxyFactory proxyFactory;
	private ValueInjector valueInjector;
	private HtmlCleaner htmlCleaner;
	private boolean showDebugBar;
	private TagNode debugBarNode;
	
	private static final FieldPathHelper fieldPathHelper = new FieldPathHelper();
	private static final int processIncludesMaxDepth = 10;
	final Logger logger = LoggerFactory.getLogger(FormParser.class);

	public FormParser(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
		this.selectOptionHelper = new SelectOptionHelper(resourceLoader);
		this.proxyFactory = new ProxyFactory();
		this.valueInjector = new ValueInjector();
		CleanerProperties cleanerProperties = new CleanerProperties();
		cleanerProperties.setAllowHtmlInsideAttributes(true);
		this.htmlCleaner = new HtmlCleaner(cleanerProperties);
		showDebugBar = RhinoformsProperties.getInstance().isShowDebugBar();
		debugBarNode = loadDebugBar(resourceLoader);
	}

	public void parseForm(String formContents, FormFlow formFlow, PrintWriter writer, JSMasterScope masterScope, boolean suppressDebugBar) throws XPatherException,
			XPathExpressionException, IOException, ResourceLoaderException, FormParserException {

		TagNode formHtml = htmlCleaner.clean(formContents);
		String flowID = formFlow.getId();

		Document dataDocument = formFlow.getDataDocument();
		String docBase = formFlow.getCurrentDocBase();
		String currentPath = formFlow.getCurrentPath();
		
		// Process rf.include
		processIncludes(formHtml, formFlow);
		
		// Add debugBar
		if (showDebugBar && !suppressDebugBar) {
			TagNode body = formHtml.findElementByName("body", false);
			int size = body.getChildren().size();
			body.insertChild(size, debugBarNode);
		}

		// Process rf.forEach statements
		valueInjector.processForEachStatements(formHtml, dataDocument, docBase);
		valueInjector.processRemainingCurlyBrackets(formHtml, dataDocument, docBase, flowID);

		Object[] rfFormNodes = formHtml.evaluateXPath("//form[@" + Constants.RHINOFORMS_FLAG + "='true']");
		if (rfFormNodes.length > 0) {
			logger.debug("{} forms found.", rfFormNodes.length);
			TagNode formNode = (TagNode) rfFormNodes[0];

			// Process dynamic select elements
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

			// Process range select elements
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

			// Record input fields
			List<InputPojo> inputPojos = new ArrayList<InputPojo>();
			Map<String, InputPojo> inputPojosMap = new HashMap<String, InputPojo>();

			@SuppressWarnings("unchecked")
			List<TagNode> inputs = formNode.getElementListByName("input", true);
			@SuppressWarnings("unchecked")
			List<TagNode> selects = formNode.getElementListByName("select", true);
			inputs.addAll(selects);
			for (TagNode inputTagNode : inputs) {
				String name = inputTagNode.getAttributeByName(Constants.NAME_ATTR);
				if (name != null) {
					String type;

					if (inputTagNode.getName().equals("select")) {
						type = "select";
					} else {
						type = inputTagNode.getAttributeByName(Constants.TYPE_ATTR);
					}
					
					if (type != null) {

						if (!(type.equals("radio") && inputPojosMap.containsKey(name))) {
	
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
							if (type.equals("radio")) {
								String value = inputTagNode.getAttributeByName(Constants.VALUE_ATTR);
								if (inputValue.equals(value)) {
									inputTagNode.setAttribute(Constants.CHECKED_ATTR, Constants.CHECKED_ATTR);
								}
							} else if (type.equals("checkbox")) {
								if (inputValue.equals("true")) {
									inputTagNode.setAttribute(Constants.CHECKED_ATTR, Constants.CHECKED_ATTR);
								}
							} else if (type.equals("select")) {
								Object[] nodes = inputTagNode.evaluateXPath("option[@value=\"" + inputValue + "\"]");
								if (nodes.length == 0) {
									nodes = inputTagNode.evaluateXPath("option[text()=\"" + inputValue + "\"]");
								}
								if (nodes.length > 0) {
									((TagNode) nodes[0]).setAttribute(Constants.SELECTED_ATTR, "selected");
								}
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

			// Process auto-complete fields, replace source with proxy path
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

			// Add flowId as hidden field
			TagNode flowIdNode = new TagNode("input");
			flowIdNode.setAttribute("name", Constants.FLOW_ID_FIELD_NAME);
			flowIdNode.setAttribute("type", "hidden");
			flowIdNode.setAttribute("value", flowID + "");
			formNode.insertChild(0, flowIdNode);

			// Mark form as parsed
			formNode.setAttribute("parsed", "true");
		} else {
			logger.warn("No forms found");
		}
		
		// Write out processed document
		new SimpleHtmlSerializer(htmlCleaner.getProperties()).write(formHtml, writer, "utf-8");
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
				InputStream resourceAsStream = resourceLoader.getResourceAsStream(srcAttribute);
				if (resourceAsStream != null) {
					TagNode includeHtml = htmlCleaner.clean(resourceAsStream);
					TagNode body = includeHtml.findElementByName("body", false);
					doProcessIncludes(body, depth + 1, formFlow);
					
					@SuppressWarnings("unchecked")
					List<HtmlNode> bodyChildren = body.getChildren();
					Collections.reverse(bodyChildren);
					TagNode includeParent = includeNode.getParent();
					for (HtmlNode bodyChild : bodyChildren) {
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
	
	private TagNode loadDebugBar(ResourceLoader resourceLoader) {
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

}
