package com.rhinoforms.formparser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.output.StringBuilderWriter;
import org.htmlcleaner.ContentNode;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.HtmlNode;
import org.htmlcleaner.SimpleHtmlSerializer;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.rhinoforms.Constants;
import com.rhinoforms.TestApplicationContext;
import com.rhinoforms.flow.FormFlow;
import com.rhinoforms.flow.FormFlowFactoryException;
import com.rhinoforms.resourceloader.ResourceLoaderException;
import com.rhinoforms.xml.DocumentHelper;

public class ValueInjector {

	private static final Pattern CURLY_BRACKET_CONTENTS_PATTERN = Pattern.compile(".*?\\{\\{([^} ]+)\\}\\}.*", Pattern.DOTALL);
	private static final XPathFactory xPathFactory = XPathFactory.newInstance();
	private HtmlCleaner htmlCleaner;
	private SimpleHtmlSerializer simpleHtmlSerializer;
	private DocumentHelper documentHelper;
	
	private final Logger logger = LoggerFactory.getLogger(ValueInjector.class);

	public ValueInjector(HtmlCleaner htmlCleaner, SimpleHtmlSerializer simpleHtmlSerializer) {
		this.htmlCleaner = htmlCleaner;
		this.simpleHtmlSerializer = simpleHtmlSerializer;
		documentHelper = new DocumentHelper();
	}

	public void processForEachStatements(FormFlow formFlow, TagNode formHtml, Document dataDocument, String docBase) throws XPatherException,
			XPathExpressionException, IOException, ValueInjectorException {
		Object[] forEachNodes = formHtml.evaluateXPath("//" + Constants.FOR_EACH_ELEMENT);
		logger.debug("Processing {} nodes. Found: {}", Constants.FOR_EACH_ELEMENT, forEachNodes.length);
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
				StringBuilder forEachNodeContents = nodeToStringBuilder(forEachNode);
				for (int dataNodeindex = 0; dataNodeindex < dataNodeList.getLength(); dataNodeindex++) {
					Node dataNode = dataNodeList.item(dataNodeindex);
					StringBuilder thisForEachNodeContents = new StringBuilder(forEachNodeContents);
					replaceCurlyBrackets(formFlow, thisForEachNodeContents, dataDocument, dataNode, selectAsName, dataNodeindex + 1);
					
					System.out.println("thisForEachNodeContents:");
					System.out.println(thisForEachNodeContents);
					
					TagNode processedForEachNode = stringBuilderToNode(thisForEachNodeContents);
					
					StringWriter stringWriter = new StringWriter();
					new SimpleHtmlSerializer(htmlCleaner.getProperties()).write(processedForEachNode, new PrintWriter(stringWriter), "utf-8");
					System.out.println("processedForEachNode:");
					System.out.println(stringWriter);
					
					@SuppressWarnings("unchecked")
					List<HtmlNode> children = processedForEachNode.getChildren();
					trimFirstNewline(children);
					for (HtmlNode child : children) {
						parent.insertChildBefore(forEachNode, child);
					}
				}
				parent.removeChild(forEachNode);
			} else {
				String message = "'select' attribute is empty or missing";
				logger.warn("forEach error - {}", message);
				forEachNode.setAttribute("error", message);
			}
		}
	}

	private void trimFirstNewline(List<HtmlNode> children) {
		if (!children.isEmpty()) {
			HtmlNode htmlNode = children.get(0);
			if (htmlNode instanceof ContentNode) {	
				ContentNode contentNode = (ContentNode) htmlNode;
				StringBuilder content = contentNode.getContent();
				if (content.length() >= 1 && content.substring(0, 1).equals("\n")) {
					content.delete(0, 1);
				} else if (content.length() >= 2 && content.substring(0, 2).equals("\r\n")) {
					content.delete(0, 2);
				}
			}
		}
	}

	public void processRemainingCurlyBrackets(FormFlow formFlow, TagNode formHtml, Document dataDocument, String docBase) throws IOException,
			XPathExpressionException, ValueInjectorException {
		XPathExpression selectExpression = xPathFactory.newXPath().compile(docBase);
		Node dataDocAtDocBase = (Node) selectExpression.evaluate(dataDocument, XPathConstants.NODE);

		TagNode[] bodyElements = formHtml.getElementsByName("body", false);
		if (bodyElements.length > 0) {
			TagNode bodyElement = bodyElements[0];
			StringBuilder builder = nodeToStringBuilder(bodyElement);
			replaceCurlyBrackets(formFlow, builder, dataDocAtDocBase, null, null, null);
			TagNode processedBodyElement = stringBuilderBodyToNode(builder);
			TagNode parent = bodyElement.getParent();
			parent.replaceChild(bodyElement, processedBodyElement);
		}
	}

	public void replaceCurlyBrackets(FormFlow formFlow, StringBuilder builder, Node dataDocument)
			throws ValueInjectorException {
		replaceCurlyBrackets(formFlow, builder, dataDocument, null, null, null);
	}
	
	private void replaceCurlyBrackets(FormFlow formFlow, StringBuilder builder, Node dataDocument, Node contextNode, String contextName, Integer contextindex)
			throws ValueInjectorException {
		StringBuffer completedText = new StringBuffer();
		
		String flowID = formFlow.getId();
		Properties properties = formFlow.getProperties();

		Matcher matcher = CURLY_BRACKET_CONTENTS_PATTERN.matcher(builder);
		while (matcher.matches()) {
			// get brackets contents
			String group = matcher.group(1);
			String value = null;
			if (group.startsWith("$")) {
				if (group.length() > 1 && properties != null) {
					value = properties.getProperty(group.substring(1));
				}
			} else if (group.equals(Constants.FLOW_ID_FIELD_NAME) && flowID != null) {
				value = flowID;
			} else {
				if (contextNode != null && group.equals(contextName)) {
					Node firstChild = contextNode.getFirstChild();
					if (firstChild != null) {
						value = firstChild.getTextContent();
					}
				} else if (contextNode != null && group.startsWith(contextName + ".")) {
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

	private String lookupValue(Node dataNode, String fieldName) throws ValueInjectorException {
		if (dataNode != null) {
			String xpath = null;
			XPathExpression expression = null;
			try {
				xpath = fieldName.replaceAll("\\.", "/");
				expression = XPathFactory.newInstance().newXPath().compile(xpath);
				return expression.evaluate(dataNode);
				
			} catch (XPathExpressionException e) {
				StringBuilder stringBuilder = new StringBuilder();
				if (expression == null) {
					stringBuilder.append("Failed to compile xpath '").append(xpath).append("'");
				} else {
					String documentAsString = "";
					try {
						documentAsString = documentHelper.documentToString(dataNode);
					} catch (TransformerException e1) {
						documentAsString = "TransformerException serialising document.";
					}
					stringBuilder.append("Failed to evaluate xpath '").append(xpath).append("', DataDocument: ").append(documentAsString);
				}
				throw new ValueInjectorException(stringBuilder.toString());
			}
		} else {
			throw new ValueInjectorException("Document node is null.");
		}
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
	
	public static void main(String[] args) throws ResourceLoaderException, IOException {
		ValueInjector valueInjector = new TestApplicationContext().getValueInjector();
		TagNode node = valueInjector.stringBuilderBodyToNode(new StringBuilder("<a>123</a>"));
		System.out.println(node.getChildren().get(0));
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

	public void processFlowDefinitionCurlyBrackets(StringBuilder flowStringBuilder, Properties flowProperties) throws FormFlowFactoryException {
		Matcher matcher = CURLY_BRACKET_CONTENTS_PATTERN.matcher(flowStringBuilder);
		while (matcher.matches()) {
			String group = matcher.group(1);
			String property = flowProperties.getProperty(group);
			if (property != null) {
				int groupStart = flowStringBuilder.indexOf("{{" + group + "}}");
				int groupEnd = groupStart + group.length() + 4;
				flowStringBuilder.replace(groupStart, groupEnd, property);
				matcher = CURLY_BRACKET_CONTENTS_PATTERN.matcher(flowStringBuilder);
			} else {
				throw new FormFlowFactoryException("Property not found '" + group + "'");
			}
		}
	}

}
