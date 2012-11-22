package com.rhinoforms.formparser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.output.StringBuilderWriter;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.SimpleHtmlSerializer;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.rhinoforms.Constants;

public class ValueInjector {

	private static final Pattern CURLY_BRACKET_CONTENTS_PATTERN = Pattern.compile(".*?\\{\\{([^} ]+)\\}\\}.*", Pattern.DOTALL);
	private static final XPathFactory xPathFactory = XPathFactory.newInstance();
	private HtmlCleaner htmlCleaner;
	private SimpleHtmlSerializer simpleHtmlSerializer;
	
	final Logger logger = LoggerFactory.getLogger(ValueInjector.class);

	public ValueInjector() {
		htmlCleaner = new HtmlCleaner();
		CleanerProperties properties = htmlCleaner.getProperties();
		properties.setOmitXmlDeclaration(true);
		simpleHtmlSerializer = new SimpleHtmlSerializer(properties);
	}

	public void processForEachStatements(TagNode formHtml, Document dataDocument, String docBase) throws XPatherException,
			XPathExpressionException, IOException {
		Object[] forEachNodes = formHtml.evaluateXPath("//" + Constants.FOR_EACH_ELEMENT);
		for (Object forEachNodeO : forEachNodes) {
			TagNode forEachNode = (TagNode) forEachNodeO;
			TagNode parent = forEachNode.getParent();
			String selectPath = forEachNode.getAttributeByName("select");
			String selectAsName = forEachNode.getAttributeByName("as");

			if (selectPath != null && !selectPath.isEmpty()) {
				String xpath;
				if (!selectPath.startsWith("/")) {
					xpath = docBase + "/" + selectPath;
				} else {
					xpath = selectPath;
				}
				XPathExpression selectExpression = xPathFactory.newXPath().compile(xpath);
				NodeList dataNodeList = (NodeList) selectExpression.evaluate(dataDocument, XPathConstants.NODESET);
				for (int dataNodeindex = 0; dataNodeindex < dataNodeList.getLength(); dataNodeindex++) {
					Node dataNode = dataNodeList.item(dataNodeindex);
					
					StringBuilder forEachNodeContents = nodeToStringBuilder(forEachNode);
					replaceCurlyBrackets(forEachNodeContents, dataDocument, null, dataNode, selectAsName, dataNodeindex + 1);
					TagNode processedForEachNode = stringBuilderToNode(forEachNodeContents);
					
					parent.addChildren(processedForEachNode.getChildren());
				}
				parent.removeChild(forEachNode);
			} else {
				String message = "'select' attribute is empty or missing";
				logger.warn("forEach error - {}", message);
				forEachNode.setAttribute("error", message);
			}
		}
	}

	public void processRemainingCurlyBrackets(TagNode formHtml, Document dataDocument, String docBase, String flowID) throws IOException,
			XPathExpressionException {
		XPathExpression selectExpression = xPathFactory.newXPath().compile(docBase);
		Node dataDocAtDocBase = (Node) selectExpression.evaluate(dataDocument, XPathConstants.NODE);

		TagNode[] bodyElements = formHtml.getElementsByName("body", false);
		if (bodyElements.length > 0) {
			TagNode bodyElement = bodyElements[0];
			StringBuilder builder = nodeToStringBuilder(bodyElement);
			replaceCurlyBrackets(builder, dataDocAtDocBase, flowID, null, null, null);
			TagNode processedBodyElement = stringBuilderBodyToNode(builder);
			TagNode parent = bodyElement.getParent();
			parent.replaceChild(bodyElement, processedBodyElement);
		}
	}

	private void replaceCurlyBrackets(StringBuilder builder, Node dataDocument, String flowID, Node contextNode, String contextName, Integer contextindex)
			throws XPathExpressionException {
		StringBuffer completedText = new StringBuffer();

		Matcher matcher = CURLY_BRACKET_CONTENTS_PATTERN.matcher(builder);
		while (matcher.matches()) {
			// get brackets contents
			String group = matcher.group(1);
			String value = null;
			if (group.equals(Constants.FLOW_ID_FIELD_NAME) && flowID != null) {
				value = flowID;
			} else {
				if (contextNode != null && group.startsWith(contextName + ".")) {
					if (group.equals(contextName + ".index")) {
						value = "" + contextindex;
					} else {
						// lookup value from context node
						value = lookupValue(contextNode, group.substring(contextName.length() + 1));
					}
				} else {
					// lookup value from main dataDoc
					value = lookupValue(dataDocument, group);
				}
			}

			int groupStart = builder.indexOf("{{" + group + "}}");
			int groupEnd = groupStart + group.length() + 4;

			int end;
			if (value != null) {
				builder.replace(groupStart, groupEnd, value);
				end = groupStart + value.length();
			} else {
				end = groupEnd;
			}
			completedText.append(builder.subSequence(0, end));
			builder.delete(0, end);
			matcher = CURLY_BRACKET_CONTENTS_PATTERN.matcher(builder);
		}

		completedText.append(builder);
		builder.delete(0, builder.length());
		builder.append(completedText);
	}

	private String lookupValue(Node dataNode, String fieldName) throws XPathExpressionException {
		String xpath = fieldName.replaceAll("\\.", "/");
		XPathExpression expression = XPathFactory.newInstance().newXPath().compile(xpath);
		return expression.evaluate(dataNode);
	}

	StringBuilder nodeToStringBuilder(TagNode forEachNode) throws IOException {
		StringBuilderWriter forEachNodeWriter = new StringBuilderWriter();
		simpleHtmlSerializer.write(forEachNode, forEachNodeWriter, "utf-8");
		StringBuilder forEachNodeContents = forEachNodeWriter.getBuilder();
		return forEachNodeContents;
	}

	TagNode stringBuilderBodyToNode(StringBuilder nodeContents) throws IOException {
		return (TagNode) htmlCleaner.clean(nodeContents.toString()).getChildren().get(1);
	}
	
	TagNode stringBuilderToNode(StringBuilder nodeContents) throws IOException {
		return (TagNode) stringBuilderBodyToNode(nodeContents).getChildren().get(0);
	}
	
	public String serialiseNode(TagNode node) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		simpleHtmlSerializer.write(node, new OutputStreamWriter(outputStream), "utf-8");
		String actual = new String(outputStream.toByteArray());
		return actual;
	}

	public void processFlowDefinitionCurlyBrackets(StringBuilder flowStringBuilder, Properties flowProperties) {
		Matcher matcher = CURLY_BRACKET_CONTENTS_PATTERN.matcher(flowStringBuilder);
		while (matcher.matches()) {
			String group = matcher.group(1);
			String property = flowProperties.getProperty(group);
			if (property != null) {
				int groupStart = flowStringBuilder.indexOf("{{" + group + "}}");
				int groupEnd = groupStart + group.length() + 4;
				flowStringBuilder.replace(groupStart, groupEnd, property);
				matcher = CURLY_BRACKET_CONTENTS_PATTERN.matcher(flowStringBuilder);
			}
		}
	}

}
