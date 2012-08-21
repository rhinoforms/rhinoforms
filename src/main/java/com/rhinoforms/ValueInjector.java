package com.rhinoforms;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.output.StringBuilderWriter;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.SimpleHtmlSerializer;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ValueInjector {

	private static final Pattern CURLY_BRACKET_CONTENTS_PATTERN = Pattern.compile(".*?\\{([^} ]+)\\}.*", Pattern.DOTALL);
	private static final XPathFactory xPathFactory = XPathFactory.newInstance();
	private HtmlCleaner htmlCleaner;

	public ValueInjector() {
		this.htmlCleaner = new HtmlCleaner();
	}

	public void processForEachStatements(TagNode formHtml, Document dataDocument, String docBase) throws XPatherException,
			XPathExpressionException, IOException {
		Object[] forEachNodes = formHtml.evaluateXPath("//rf.forEach");
		for (Object forEachNodeO : forEachNodes) {
			TagNode forEachNode = (TagNode) forEachNodeO;
			TagNode parent = forEachNode.getParent();
			String selectPath = forEachNode.getAttributeByName("select");
			String selectAsName = forEachNode.getAttributeByName("as");

			XPathExpression selectExpression = xPathFactory.newXPath().compile(docBase + "/" + selectPath);
			NodeList dataNodeList = (NodeList) selectExpression.evaluate(dataDocument, XPathConstants.NODESET);
			for (int dataNodeindex = 0; dataNodeindex < dataNodeList.getLength(); dataNodeindex++) {
				Node dataNode = dataNodeList.item(dataNodeindex);

				StringBuilder forEachNodeContents = nodeToStringBuilder(forEachNode);
				replaceCurlyBrackets(forEachNodeContents, dataDocument, dataNode, selectAsName, dataNodeindex + 1);
				TagNode processedForEachNode = stringBuilderToNode(forEachNodeContents);

				parent.addChildren(processedForEachNode.getChildren());
			}

			parent.removeChild(forEachNode);
		}
	}

	public void processRemainingCurlyBrackets(TagNode formHtml, Document dataDocument, String docBase) throws IOException,
			XPathExpressionException {
		XPathExpression selectExpression = xPathFactory.newXPath().compile(docBase);
		Node dataDocAtDocBase = (Node) selectExpression.evaluate(dataDocument, XPathConstants.NODE);

		TagNode[] bodyElements = formHtml.getElementsByName("body", false);
		if (bodyElements.length > 0) {
			TagNode bodyElement = bodyElements[0];
			StringBuilder builder = nodeToStringBuilder(bodyElement);
			replaceCurlyBrackets(builder, dataDocAtDocBase, null, null, null);
			TagNode processedBodyElement = stringBuilderToNode(builder);
			TagNode parent = bodyElement.getParent();
			parent.removeChild(bodyElement);
			parent.addChildren(processedBodyElement.getChildren());
		}
	}

	private void replaceCurlyBrackets(StringBuilder builder, Node dataDocument, Node contextNode, String contextName, Integer contextindex)
			throws XPathExpressionException {
		StringBuffer completedText = new StringBuffer();

		Matcher matcher = CURLY_BRACKET_CONTENTS_PATTERN.matcher(builder);
		while (matcher.matches()) {
			// get brackets contents
			String group = matcher.group(1);
			String value = null;
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
			matcher = CURLY_BRACKET_CONTENTS_PATTERN.matcher(builder);
		}

		completedText.append(builder);
		builder.delete(0, builder.length());
		builder.append(completedText);
	}

	private String lookupValue(Node dataNode, String fieldName) throws XPathExpressionException {
		String xpath = fieldName.replaceAll("\\.", "/");
		XPathExpression expression = XPathFactory.newInstance().newXPath().compile(xpath);
		NodeList nodeList = (NodeList) expression.evaluate(dataNode, XPathConstants.NODESET);
		if (nodeList != null && nodeList.getLength() > 0) {
			Node item = nodeList.item(0);
			return item.getTextContent();
		} else {
			return "";
		}
	}

	private StringBuilder nodeToStringBuilder(TagNode forEachNode) throws IOException {
		StringBuilderWriter forEachNodeWriter = new StringBuilderWriter();
		new SimpleHtmlSerializer(htmlCleaner.getProperties()).write(forEachNode, forEachNodeWriter, "utf-8");
		StringBuilder forEachNodeContents = forEachNodeWriter.getBuilder();
		return forEachNodeContents;
	}

	private TagNode stringBuilderToNode(StringBuilder nodeContents) throws IOException {
		TagNode newHtmlDoc = htmlCleaner.clean(nodeContents.toString());
		return (TagNode) newHtmlDoc.getChildren().get(1);
	}
	
	public String serialiseNode(TagNode node) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		new SimpleHtmlSerializer(new HtmlCleaner().getProperties()).write(node, new OutputStreamWriter(outputStream), "utf-8");
		String actual = new String(outputStream.toByteArray());
		return actual;
	}

}
