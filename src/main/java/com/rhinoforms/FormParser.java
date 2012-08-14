package com.rhinoforms;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.output.StringBuilderWriter;
import org.htmlcleaner.ContentNode;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.SimpleHtmlSerializer;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.rhinoforms.resourceloader.ResourceLoader;
import com.rhinoforms.resourceloader.ResourceLoaderException;
import com.rhinoforms.serverside.InputPojo;

public class FormParser {

	private static final FieldPathHelper fieldPathHelper = new FieldPathHelper();
	private static final XPathFactory xPathFactory = XPathFactory.newInstance();
	private static final Pattern CURLY_BRACKET_CONTENTS_PATTERN = Pattern.compile(".*?\\{([^}]+)\\}.*", Pattern.DOTALL);
	final Logger logger = LoggerFactory.getLogger(FormParser.class);

	private ResourceLoader resourceLoader;
	private SelectOptionHelper selectOptionHelper;
	private ProxyFactory proxyFactory;

	public FormParser(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
		this.selectOptionHelper = new SelectOptionHelper(resourceLoader);
		this.proxyFactory = new ProxyFactory();
	}

	public void parseForm(String formContents, FormFlow formFlow, PrintWriter writer, ScriptableObject masterScope) throws XPatherException, XPathExpressionException,
			IOException, ResourceLoaderException {

		HtmlCleaner cleaner = new HtmlCleaner();
		TagNode documentNode = cleaner.clean(formContents);

		Document dataDocument = formFlow.getDataDocument();
		String docBase = formFlow.getDocBase();
		String currentPath = formFlow.getCurrentPath();

		// Process rf.forEach statements
		Object[] forEachNodes = documentNode.evaluateXPath("//rf.forEach");
		for (Object forEachNodeO : forEachNodes) {
			TagNode forEachNode = (TagNode) forEachNodeO;
			TagNode parent = forEachNode.getParent();
			String selectPath = forEachNode.getAttributeByName("select");

			XPathExpression selectExpression = xPathFactory.newXPath().compile(docBase + "/" + selectPath);
			NodeList dataNodeList = (NodeList) selectExpression.evaluate(dataDocument, XPathConstants.NODESET);
			for (int dataNodeindex = 0; dataNodeindex < dataNodeList.getLength(); dataNodeindex++) {
				Node dataNode = dataNodeList.item(dataNodeindex);
				StringBuilderWriter forEachNodeWriter = new StringBuilderWriter();
				new SimpleHtmlSerializer(cleaner.getProperties()).write(forEachNode, forEachNodeWriter, "utf-8");
				StringBuilder forEachNodeContents = forEachNodeWriter.getBuilder();
				replaceCurlyBrackets(forEachNodeContents, dataNode, dataNodeindex + 1);
				TagNode htmlTagNode = cleaner.clean(forEachNodeContents.toString());
				TagNode body = (TagNode) htmlTagNode.getChildren().get(1);
				TagNode processedForEachNode = body.findElementByName("rf.forEach", false);
				parent.addChildren(processedForEachNode.getChildren());
			}

			parent.removeChild(forEachNode);
		}

		Object[] rfFormNodes = documentNode.evaluateXPath("//form[@" + Constants.RHINOFORMS_FLAG + "='true']");
		if (rfFormNodes.length > 0) {
			logger.debug("{} forms found.", rfFormNodes.length);
			TagNode formNode = (TagNode) rfFormNodes[0];

			// Process dynamic select elements
			Object[] dynamicSelectNodes = formNode.evaluateXPath("//select[@" + Constants.SELECT_SOURCE_ATTR + "]");
			for (Object dynamicSelectNodeO : dynamicSelectNodes) {
				TagNode dynamicSelectNode = (TagNode) dynamicSelectNodeO;
				String name = dynamicSelectNode.getAttributeByName(Constants.NAME_ATTR);
				String source = dynamicSelectNode.getAttributeByName(Constants.SELECT_SOURCE_ATTR);
				dynamicSelectNode.removeAttribute(Constants.SELECT_SOURCE_ATTR);
				logger.debug("Found dynamicSelectNode name:{}, source:{}", name, source);

				List<SelectOptionPojo> options = selectOptionHelper.loadOptions(source);
				options.add(0, new SelectOptionPojo("-- Please Select --"));
				for (SelectOptionPojo selectOptionPojo : options) {
					TagNode optionNode = new TagNode("option");
					optionNode.addChild(new ContentNode(selectOptionPojo.getText()));
					dynamicSelectNode.addChild(optionNode);
				}
			} // TODO: validate that submitted value comes from the list
			
			// Process auto-complete fields
			Object[] autoCompleteNodes = formNode.evaluateXPath("//input[@" + Constants.SELECT_SOURCE_ATTR + "]");
			for (Object autoCompleteNodeO : autoCompleteNodes) {
				TagNode autoCompleteNode = (TagNode) autoCompleteNodeO;
				String fieldName = autoCompleteNode.getAttributeByName(Constants.NAME_ATTR);
				String source = autoCompleteNode.getAttributeByName(Constants.INPUT_SOURCE_ATTR);
				
				FieldSourceProxy fieldSourceProxy = proxyFactory.createFlowProxy(currentPath, fieldName, source);
				formFlow.addFieldSourceProxy(fieldSourceProxy);
				autoCompleteNode.removeAttribute(Constants.INPUT_SOURCE_ATTR);
				autoCompleteNode.setAttribute("rf.source", "form/proxy/" + fieldSourceProxy.getProxyPath());
			}

			// Record input fields
			List<InputPojo> inputPojos = new ArrayList<InputPojo>();
			@SuppressWarnings("unchecked")
			List<TagNode> inputs = formNode.getElementListByName("input", true);
			for (TagNode inputTagNode : inputs) {
				String name = inputTagNode.getAttributeByName(Constants.NAME_ATTR);
				String type = inputTagNode.getAttributeByName(Constants.TYPE_ATTR);
				String validation = inputTagNode.getAttributeByName(Constants.VALIDATION_ATTR);
				String validationFunction = inputTagNode.getAttributeByName(Constants.VALIDATION_FUNCTION_ATTR);

				String inputValue = lookupValueByFieldName(dataDocument, name, docBase);
				if (inputValue != null) {
					inputTagNode.setAttribute("value", inputValue);
				}

				inputPojos.add(new InputPojo(name, type, validation, validationFunction));

				logger.debug("input {} - validation:{}", name, validation);
			}
			formFlow.setCurrentInputPojos(inputPojos);

			// Add flowId as hidden field
			TagNode flowIdNode = new TagNode("input");
			flowIdNode.setAttribute("name", Constants.FLOW_ID_FIELD_NAME);
			flowIdNode.setAttribute("type", "hidden");
			flowIdNode.setAttribute("value", formFlow.getId() + "");
			formNode.insertChild(0, flowIdNode);

			// Mark form as parsed
			formNode.setAttribute("parsed", "true");
		} else {
			logger.warn("No forms found");
		}

/*	Don't need to do this at the moment.	
		// Evaluate javascript on the page
		Context jsContext = Context.enter();
		try {
			Scriptable scope = jsContext.newObject(masterScope);
			String script = Constants.RHINOFORM_SCRIPT;
			jsContext.evaluateReader(scope, new InputStreamReader(resourceLoader.getResourceAsStream(script)), script, 1, null);

			Object[] rfScriptNodes = documentNode.evaluateXPath("//script[@" + Constants.RHINOFORMS_FLAG + "='true']");
			TagNode rfScriptNode = null;
			for (Object rfScriptNodeObject : rfScriptNodes) {
				rfScriptNode = (TagNode) rfScriptNodeObject;
				StringBuffer rfScriptNodeScript = rfScriptNode.getText();
				String rfScriptNodeScriptText = rfScriptNodeScript.toString();
				jsContext.evaluateString(scope, rfScriptNodeScriptText, "<cmd>", 1, null);
			}
		} finally {
			Context.exit();
		}
*/
		// Write out processed document
		new SimpleHtmlSerializer(cleaner.getProperties()).write(documentNode, writer, "utf-8");
	}

	private String lookupValueByFieldName(Node document, String name, String docBase) throws XPathExpressionException {
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

	private void replaceCurlyBrackets(StringBuilder builder, Node node, int dataNodeindex) throws XPathExpressionException {
		StringBuffer completedText = new StringBuffer();

		String nodeName = node.getNodeName();
		Matcher matcher = CURLY_BRACKET_CONTENTS_PATTERN.matcher(builder);
		while (matcher.matches()) {
			// get brackets contents
			String group = matcher.group(1);
			if (group.startsWith(nodeName + ".")) {

				String value = null;
				if (group.equals(nodeName + ".index")) {
					value = "" + dataNodeindex;
				} else {
					// lookup value from current node
					String xpath = group.substring(nodeName.length() + 1).replaceAll("\\.", "/");
					XPathExpression expression = XPathFactory.newInstance().newXPath().compile(xpath);
					NodeList nodeList = (NodeList) expression.evaluate(node, XPathConstants.NODESET);
					if (nodeList != null && nodeList.getLength() > 0) {
						Node item = nodeList.item(0);
						value = item.getTextContent();
					}
				}

				int groupStart = builder.indexOf("{" + group + "}");
				int groupEnd = groupStart + group.length() + 2;

				int end;
				if (value != null) {
					builder.replace(groupStart, groupEnd, value);
					end = groupStart + value.length();
				} else {
					end = groupEnd;
				}
				completedText.append(builder.subSequence(0, end));
				builder.delete(0, end);
			}
			matcher = CURLY_BRACKET_CONTENTS_PATTERN.matcher(builder);
		}

		completedText.append(builder);
		builder.delete(0, builder.length());
		builder.append(completedText);
	}

}
