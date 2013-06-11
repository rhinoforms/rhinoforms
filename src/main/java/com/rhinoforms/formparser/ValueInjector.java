package com.rhinoforms.formparser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.rhinoforms.Constants;
import com.rhinoforms.flow.FormFlowFactoryException;
import com.rhinoforms.xml.DocumentHelper;

public class ValueInjector {

	private static final Pattern CURLY_BRACKET_CONTENTS_PATTERN = Pattern.compile(".*?\\{\\{([^} ]+)\\}\\}.*", Pattern.DOTALL);
	private static final Pattern CURLY_BRACKET_PROPERTY_PATTERN = Pattern.compile(".*?\\{\\{\\$([^} ]+)\\}\\}.*", Pattern.DOTALL);
	private static final XPathFactory xPathFactory = XPathFactory.newInstance();
	private HtmlCleaner htmlCleaner;
	private SimpleHtmlSerializer simpleHtmlSerializer;
	private DocumentHelper documentHelper;
	private HtmlTags tags;
	
	private final Logger logger = LoggerFactory.getLogger(ValueInjector.class);

	public ValueInjector(HtmlCleaner htmlCleaner, SimpleHtmlSerializer simpleHtmlSerializer, HtmlTags tags) {
		this.htmlCleaner = htmlCleaner;
		this.simpleHtmlSerializer = simpleHtmlSerializer;
		this.tags = tags;
		documentHelper = new DocumentHelper();
	}
	
	public void processHtmlTemplate(InputStream htmlTemplateInputStream, Document dataDocument, String docBase, Properties properties, OutputStream processedHtmlOutputStream) throws IOException, XPathExpressionException, ValueInjectorException {
		TagNode html = htmlCleaner.clean(htmlTemplateInputStream);
		if (docBase == null) {
			docBase = "";
		}
		processForEachStatements(properties, html, dataDocument, docBase);
		processCurlyBrackets(dataDocument, html, properties, docBase);
		
		TagNode bodyElement = html.getElementsByName("body", false)[0];
		serializeChildren(bodyElement, processedHtmlOutputStream);
	}

	private void serializeChildren(TagNode bodyElement, OutputStream processedHtmlOutputStream) throws IOException {
		@SuppressWarnings("unchecked")
		List<Object> children = bodyElement.getChildren();
		OutputStreamWriter outputStreamWriter = new OutputStreamWriter(processedHtmlOutputStream);
		for (Object object : children) {
			if (object instanceof ContentNode) {
				ContentNode contentNode = (ContentNode) object;
				contentNode.serialize(simpleHtmlSerializer, outputStreamWriter);
			} else if (object instanceof TagNode) {
				TagNode tagNode = (TagNode) object;
				tagNode.serialize(simpleHtmlSerializer, outputStreamWriter);
			}
		}
		outputStreamWriter.flush();
	}

	public void processForEachStatements(Properties properties, TagNode formHtml, Document dataDocument, String docBase) throws XPathExpressionException, IOException, ValueInjectorException {
		ArrayList<TagNode> forEachNodes = new ArrayList<TagNode>();
		Collections.addAll(forEachNodes, formHtml.getElementsByName(tags.getForEachTag(), true));

		filterNestedForEachElements(forEachNodes);
		
		logger.debug("Processing {} nodes. Found at top level: {}", new Object[] {tags.getForEachTag(), forEachNodes.size()});
		
		for (TagNode forEachNode : forEachNodes) {
			Map<String, Node> contextNodes = new HashMap<String, Node>();
			processForEachStatements(properties, forEachNode, forEachNode, dataDocument, docBase, contextNodes, 0);
		}
	}	
	
	public void processForEachStatements(Properties properties, TagNode forEachNode, TagNode markerNode, Document dataDocument, String docBase, Map<String, Node> contextNodes, int level) throws XPathExpressionException, IOException, ValueInjectorException {
		String selectPath = forEachNode.getAttributeByName("select");
		String selectAsName = forEachNode.getAttributeByName("as");
		StringBuilder forEachNodeContents = nodeToStringBuilder(forEachNode);
		TagNode markerNodeParent = markerNode.getParent();

		if (selectPath != null && !selectPath.isEmpty()) {
			String selectXpath;
			if (!selectPath.startsWith("/")) {
				selectXpath = docBase + "/" + selectPath;
			} else {
				selectXpath = selectPath;
			}
			XPathExpression selectExpression = xPathFactory.newXPath().compile(selectXpath);
			NodeList dataNodeList = (NodeList) selectExpression.evaluate(dataDocument, XPathConstants.NODESET);
			for (int dataNodeindex = 0; dataNodeindex < dataNodeList.getLength(); dataNodeindex++) {
				Node dataNode = dataNodeList.item(dataNodeindex);
				contextNodes.put(selectAsName, dataNode);
				
				// Process nested for loops
				TagNode[] nestedForEachNodeArray = forEachNode.getElementsByName(tags.getForEachTag(), true);
				List<TagNode> nestedForEachNodes = Arrays.asList(nestedForEachNodeArray);
				filterNestedForEachElements(nestedForEachNodes);
				logger.debug("Processing nested {} nodes. Level: {}, Found: {}", new Object[] {tags.getForEachTag(), level, nestedForEachNodes.size()});
				for (TagNode nestedForEachNode : nestedForEachNodes) {
					String forEachIterationDocBase = selectXpath += "[" + (dataNodeindex + 1) + "]";
					processForEachStatements(properties, nestedForEachNode, markerNode, dataDocument, forEachIterationDocBase, contextNodes, level + 1);
				}
				
				StringBuilder thisForEachNodeContents = new StringBuilder(forEachNodeContents);
				replaceCurlyBrackets(properties, thisForEachNodeContents, dataDocument, contextNodes, dataNodeindex + 1);
				contextNodes.remove(selectAsName);
				
				TagNode processedForEachNode = stringBuilderToNode(thisForEachNodeContents);
				@SuppressWarnings("unchecked")
				List<HtmlNode> children = processedForEachNode.getChildren();
				trimFirstNewline(children);
				if (dataNodeindex == dataNodeList.getLength() - 1) {
					trimTrailingWhitespace(children);
				}
				for (HtmlNode child : children) {
					if (!(child instanceof TagNode) || !((TagNode) child).getName().equals(tags.getForEachTag())) {
						markerNodeParent.insertChildBefore(markerNode, child);
					}
				}
			}
			if (forEachNode == markerNode) {
				markerNodeParent.removeChild(markerNode);
			}
		} else {
			String message = "'select' attribute is empty or missing";
			logger.warn("forEach error - {}", message);
			forEachNode.setAttribute("error", message);
		}
	}

	private void filterNestedForEachElements(List<TagNode> forEachNodes) {
		HashSet<TagNode> tagNodesToRemove = new HashSet<TagNode>();
		for (TagNode tagNode : forEachNodes) {
			if (hasForEachParent(tagNode)) {
				tagNodesToRemove.remove(tagNode);
			}
		}
		forEachNodes.removeAll(tagNodesToRemove);
	}

	private boolean hasForEachParent(TagNode tagNode) {
		TagNode parent = tagNode.getParent();
		if (parent != null) {
			if (!parent.getName().equals(tags.getForEachTag())) {
				return hasForEachParent(parent);
			} else {
				return true;
			}
		}
		return false;
	}

	private void trimFirstNewline(List<HtmlNode> children) {
		if (!children.isEmpty()) {
			HtmlNode htmlNode = children.get(0);
			if (htmlNode instanceof ContentNode) {	
				ContentNode contentNode = (ContentNode) htmlNode;
				StringBuilder content = contentNode.getContent();
				if (content.length() > 0 && content.substring(0, 1).equals("\n")) {
					content.delete(0, 1);
				} else if (content.length() >= 2 && content.substring(0, 2).equals("\r\n")) {
					content.delete(0, 2);
				}
			}
		}
	}
	
	private void trimTrailingWhitespace(List<HtmlNode> children) {
		HtmlNode htmlNode = children.get(children.size() - 1);
		if (htmlNode instanceof ContentNode) {
			ContentNode contentNode = (ContentNode) htmlNode;
			StringBuilder contentBuilder = contentNode.getContent();
			String content = contentBuilder.toString();
			String trailingWhitespaceRegex = "\\s+$";
			if (content.matches(trailingWhitespaceRegex)) {
				content = content.replaceFirst(trailingWhitespaceRegex, "");
				contentBuilder.setLength(0);
				contentBuilder.append(content);
			}
		}
	}

	public void processCurlyBrackets(Document dataDocument, TagNode formHtml, Properties properties, String docBase) throws IOException,
			ValueInjectorException {

		XPathExpression selectExpression;
		Node dataDocAtDocBase;
		
		try {
			selectExpression = xPathFactory.newXPath().compile(docBase);
			dataDocAtDocBase = (Node) selectExpression.evaluate(dataDocument, XPathConstants.NODE);
		} catch (XPathExpressionException e) {
			throw new ValueInjectorException("Invalid docBase '" + docBase + "'", e);
		}

		TagNode[] bodyElements = formHtml.getElementsByName("body", false);
		if (bodyElements.length > 0) {
			TagNode bodyElement = bodyElements[0];
			StringBuilder builder = nodeToStringBuilder(bodyElement);
			replaceCurlyBrackets(properties, builder, dataDocAtDocBase);
			TagNode processedBodyElement = stringBuilderBodyToNode(builder);
			TagNode parent = bodyElement.getParent();
			parent.replaceChild(bodyElement, processedBodyElement);
		}
	}

	public void replaceCurlyBrackets(Properties properties, StringBuilder builder, Node dataDocument)
			throws ValueInjectorException {
		replaceCurlyBrackets(properties, builder, dataDocument,new HashMap<String, Node>(), null);
	}
	
	private void replaceCurlyBrackets(Properties properties, StringBuilder builder, Node dataDocument, Map<String, Node> contextNodes, Integer contextindex)
			throws ValueInjectorException {
		StringBuffer completedText = new StringBuffer();
		
		Matcher matcher = CURLY_BRACKET_CONTENTS_PATTERN.matcher(builder);
		while (matcher.matches()) {
			// get brackets contents
			String group = matcher.group(1);
			String value = null;
			if (group.startsWith("$")) {
				if (group.length() > 1 && properties != null) {
					value = properties.getProperty(group.substring(1));
				}
			} else {
				String groupContextName = group.split("\\.")[0];
				if (contextNodes.containsKey(groupContextName)) {
					Node contextNode = contextNodes.get(groupContextName);
					if (group.equals(groupContextName)) {
						Node firstChild = contextNode.getFirstChild();
						if (firstChild != null) {
							value = firstChild.getTextContent();
						}
					} else {
						if (group.equals(groupContextName + ".index")) {
							value = "" + contextindex;
						} else {
							// lookup value from context node
							value = lookupValue(contextNode, group.substring(groupContextName.length() + 1));
						}
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
		simpleHtmlSerializer.write(forEachNode, forEachNodeWriter, Constants.UTF8);
		StringBuilder forEachNodeContents = forEachNodeWriter.getBuilder();
		return forEachNodeContents;
	}

	TagNode stringBuilderBodyToNode(StringBuilder nodeContents) throws IOException {
		return (TagNode) htmlCleaner.clean(nodeContents.toString()).getChildren().get(1);
	}
	
	TagNode stringBuilderToNode(StringBuilder nodeContents) throws IOException {
		return (TagNode) stringBuilderBodyToNode(nodeContents).getChildren().get(0);
	}
	
	public void processFlowDefinitionCurlyBrackets(StringBuilder flowStringBuilder, Properties flowProperties) throws FormFlowFactoryException {
		if (flowProperties != null) {
			Matcher matcher = CURLY_BRACKET_PROPERTY_PATTERN.matcher(flowStringBuilder);
			while (matcher.find()) {
				String group = matcher.group(1);
				String property = flowProperties.getProperty(group);
				if (property != null) {
					int groupStart = flowStringBuilder.indexOf("{{$" + group + "}}");
					int groupEnd = groupStart + group.length() + 5;
					flowStringBuilder.replace(groupStart, groupEnd, property);
					matcher = CURLY_BRACKET_PROPERTY_PATTERN.matcher(flowStringBuilder);
				} else {
					throw new FormFlowFactoryException("Property not found '" + group + "'");
				}
			}
		}
	}

}
