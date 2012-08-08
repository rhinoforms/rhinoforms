package com.rhinoforms;

import java.io.IOException;
import java.io.InputStreamReader;
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
import org.apache.log4j.Logger;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.SimpleHtmlSerializer;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.rhinoforms.serverside.InputPojo;

public class FormParser {

	private ResourceLoader resourceLoader;
	
	private static final FieldPathHelper fieldPathHelper = new FieldPathHelper();
	private static final XPathFactory xPathFactory = XPathFactory.newInstance();
	private static final Pattern CURLY_BRACKET_CONTENTS_PATTERN = Pattern.compile(".*?\\{([^}]+)\\}.*", Pattern.DOTALL);
	private static final Logger LOGGER = Logger.getLogger(FormParser.class);

	public FormParser(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	public void parseForm(String formContents, FormFlow formFlow, PrintWriter writer) throws XPatherException, XPathExpressionException,
			IOException {

		HtmlCleaner cleaner = new HtmlCleaner();
		TagNode documentNode = cleaner.clean(formContents);

		Document dataDocument = formFlow.getDataDocument();
		String docBase = formFlow.getDocBase();

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

		Object[] rfFormNodes = documentNode.evaluateXPath("//form[@" + Constants.RHINOFORM_FLAG + "='true']");
		if (rfFormNodes.length > 0) {
			LOGGER.debug(rfFormNodes.length + " forms found.");
			TagNode formNode = (TagNode) rfFormNodes[0];

			List<InputPojo> inputPojos = new ArrayList<InputPojo>();
			@SuppressWarnings("unchecked")
			List<TagNode> inputs = formNode.getElementListByName("input", false);
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

				LOGGER.debug("input " + name + " - validation:" + validation);
			}

			formFlow.setCurrentInputPojos(inputPojos);

			formNode.setAttribute("parsed", "true");
			TagNode flowIdNode = new TagNode("input");
			flowIdNode.setAttribute("name", Constants.FLOW_ID_FIELD_NAME);
			flowIdNode.setAttribute("type", "hidden");
			flowIdNode.setAttribute("value", formFlow.getId() + "");
			formNode.insertChild(0, flowIdNode);

			Scriptable scope = formFlow.getScope();

			String script = Constants.RHINOFORM_SCRIPT;
			Context jsContext = Context.enter();
			try {
				jsContext.evaluateReader(scope, new InputStreamReader(resourceLoader.getResourceAsStream(script)), script, 1, null);

				Object[] rfScriptNodes = documentNode.evaluateXPath("//script[@" + Constants.RHINOFORM_FLAG + "='true']");
				TagNode rfScriptNode = null;
				for (Object rfScriptNodeObject : rfScriptNodes) {
					rfScriptNode = (TagNode) rfScriptNodeObject;
					StringBuffer rfScriptNodeScript = rfScriptNode.getText();
					String rfScriptNodeScriptText = rfScriptNodeScript.toString();
					jsContext.evaluateString(scope, rfScriptNodeScriptText, "<cmd>", 1, null);
				}
				new SimpleHtmlSerializer(cleaner.getProperties()).write(documentNode, writer, "utf-8");
			} finally {
				Context.exit();
			}
		} else {
			LOGGER.warn("No forms found");
		}
	}

	private String lookupValueByFieldName(Node document, String name, String docBase) throws XPathExpressionException {
		String inputValue = null;
		XPathExpression xPathExpression = fieldPathHelper.fieldToXPathExpression(docBase, name);
		NodeList nodeList = (NodeList) xPathExpression.evaluate(document, XPathConstants.NODESET);
		if (nodeList != null && nodeList.getLength() == 1) {
			inputValue = nodeList.item(0).getTextContent();
		} else if (nodeList != null && nodeList.getLength() > 1) {
			LOGGER.warn("Multiple nodes matched for documentBasePath: '" + docBase + "', field name: '" + name
					+ "'. No value will be pushed into the form and there may be submission problems.");
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
