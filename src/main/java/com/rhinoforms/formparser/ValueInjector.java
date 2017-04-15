package com.rhinoforms.formparser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import com.rhinoforms.util.XPathFactoryPool;
import com.rhinoforms.xml.DocumentHelper;

public class ValueInjector {

	private static final Pattern CURLY_BRACKET_CONTENTS_PATTERN = Pattern.compile(".*?\\{\\{([^}]+)\\}\\}.*", Pattern.DOTALL);
	private static final Pattern CURLY_BRACKET_PROPERTY_PATTERN = Pattern.compile(".*?\\{\\{\\$([^}]+)\\}\\}.*", Pattern.DOTALL);
	private static final String NESTED_FOR_EACH_NODE_PLACEHOLDER_ATTRIBUTE = "data-rf_nestedForEachNodePlaceholder";
	private static final XPathFactoryPool xPathFactoryPool = new XPathFactoryPool();
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
		List<TagNode> forEachNodes = Arrays.asList(formHtml.getElementsByName(tags.getForEachTag(), true));
		forEachNodes = filterNestedForEachElements(formHtml, forEachNodes);

		logger.debug("Processing {} nodes. Found at top level: {}", new Object[]{tags.getForEachTag(), forEachNodes.size()});
		for (TagNode forEachNode : forEachNodes) {
			Map<String, Node> contextNodes = new HashMap<String, Node>();
			processForEachStatement(properties, forEachNode, dataDocument, docBase, contextNodes, 0);
		}
	}

	public void processForEachStatement(Properties properties, TagNode forEachNode, Document dataDocument, String docBase, Map<String, Node> contextNodes, int level) throws XPathExpressionException, IOException, ValueInjectorException {
		String selectPath = forEachNode.getAttributeByName("select");
		String selectAsName = forEachNode.getAttributeByName("as");

		// Replace nested for loops with placeholders
		List<TagNode> nestedForEachNodes = Arrays.asList(forEachNode.getElementsByName(tags.getForEachTag(), true));
		nestedForEachNodes = filterNestedForEachElements(forEachNode, nestedForEachNodes);
		for (int i = 0; i < nestedForEachNodes.size(); i++) {
			TagNode nestedForEachNode = nestedForEachNodes.get(i);
			TagNode nestedForEachNodePlaceholder = new TagNode("div");
			nestedForEachNodePlaceholder.setAttribute(NESTED_FOR_EACH_NODE_PLACEHOLDER_ATTRIBUTE, "" + i);
			nestedForEachNode.getParent().replaceChild(nestedForEachNode, nestedForEachNodePlaceholder);
			nestedForEachNode.removeFromTree();
		}

		String forEachNodeContents = nodeToStringBuilder(forEachNode).toString();
		// Remove whitespace at beginning
		forEachNodeContents = forEachNodeContents.replaceFirst("<rf\\.foreach [^>]*?>\\s*?\n", "<rf.foreach>");

		if (selectPath != null && !selectPath.isEmpty()) {
			String selectXpath;
			if (!selectPath.startsWith("/")) {
				selectXpath = docBase + "/" + selectPath;
			} else {
				selectXpath = selectPath;
			}
			XPathFactory xPathFactory = xPathFactoryPool.getInstance();
			XPathExpression selectExpression = xPathFactory.newXPath().compile(selectXpath);
			logger.debug("Select xpath: {}", selectXpath);
			NodeList dataNodeList = (NodeList) selectExpression.evaluate(dataDocument, XPathConstants.NODESET);
			xPathFactoryPool.returnInstance(xPathFactory);
			logger.debug("Nodes found count: {}", dataNodeList.getLength());
			for (int dataNodeindex = 0; dataNodeindex < dataNodeList.getLength(); dataNodeindex++) {
				Node dataNode = dataNodeList.item(dataNodeindex);
				logger.debug("DataNode: " + dataNode.getNodeName());
				contextNodes.put(selectAsName, dataNode);

				TagNode iterationContainer = addContainerBeforeNode(forEachNode);

				logger.debug("forEachNodeContents: {}", forEachNodeContents);

				StringBuilder thisForEachNodeContents = new StringBuilder(forEachNodeContents);
				replaceCurlyBrackets(properties, thisForEachNodeContents, dataDocument, contextNodes, dataNodeindex + 1);

				logger.debug("thisForEachNodeContents: {}", thisForEachNodeContents);

				TagNode processedForEachNode = stringBuilderToNode(thisForEachNodeContents);
				@SuppressWarnings("unchecked")
				List<HtmlNode> children = processedForEachNode.getChildren();
				for (HtmlNode child : children) {
					iterationContainer.addChild(child);
				}

				// Put back nested for loops
				List<TagNode> iterationNestedForEachNodePlaceholders = Arrays.asList(iterationContainer.getElementsHavingAttribute(NESTED_FOR_EACH_NODE_PLACEHOLDER_ATTRIBUTE, true));
				for (TagNode placeholder : iterationNestedForEachNodePlaceholders) {
					TagNode parent = placeholder.getParent();
					TagNode originalNestedForEachNode = nestedForEachNodes.get(Integer.parseInt(placeholder.getAttributeByName(NESTED_FOR_EACH_NODE_PLACEHOLDER_ATTRIBUTE)));

					// Having to add then remove before insert to get the parent set correctly
					parent.addChild(originalNestedForEachNode);
					parent.removeChild(originalNestedForEachNode);
					parent.insertChildAfter(placeholder, originalNestedForEachNode);

					parent.removeChild(placeholder);
				}

				// Process nested for loops
				List<TagNode> iterationNestedForEachNodes = Arrays.asList(iterationContainer.getElementsByName(tags.getForEachTag(), true));
				iterationNestedForEachNodes = filterNestedForEachElements(iterationContainer, iterationNestedForEachNodes);
				logger.debug("Processing nested {} nodes. Level: {}, Found: {}", new Object[]{tags.getForEachTag(), level, iterationNestedForEachNodes.size()});
				if (!iterationNestedForEachNodes.isEmpty()) {
					for (TagNode nestedForEachNode : iterationNestedForEachNodes) {
						String forEachIterationDocBase = selectXpath + "[" + (dataNodeindex + 1) + "]";
						processForEachStatement(properties, nestedForEachNode, dataDocument, forEachIterationDocBase, contextNodes, level + 1);
					}
				}
				contextNodes.remove(selectAsName);

				// Unwrap container
				TagNode parent = iterationContainer.getParent();
				@SuppressWarnings("unchecked")
				List<HtmlNode> iterationContainerChildren = iterationContainer.getChildren();
				for (HtmlNode iterationContainerChild : iterationContainerChildren) {
					parent.insertChildBefore(iterationContainer, iterationContainerChild);
				}
				parent.removeChild(iterationContainer);
			}
			removeFromParentIncludeTrailingNewline(forEachNode);
		} else {
			String message = "'select' attribute is empty or missing";
			logger.warn("forEach error - {}", message);
			forEachNode.setAttribute("error", message);
		}
	}

	private void removeFromParentIncludeTrailingNewline(TagNode node) {
		TagNode parent = node.getParent();
		int childIndex = parent.getChildIndex(node);
		parent.removeChild(node);
		@SuppressWarnings("unchecked")
		List<HtmlNode> children = parent.getChildren();
		if (children.size() > childIndex) {
			HtmlNode htmlNode = children.get(childIndex);
			if (htmlNode instanceof ContentNode) {
				ContentNode contentNode = (ContentNode) htmlNode;
				StringBuilder stringBuilder = contentNode.getContent();
				String string = stringBuilder.toString();
				string = string.replaceFirst("[\r\n]*", "");
				stringBuilder.setLength(0);
				stringBuilder.append(string);
			}
		}
	}

	private TagNode addContainerBeforeNode(TagNode forEachNode) {
		TagNode container = new TagNode("div");
		TagNode parent = forEachNode.getParent();
		parent.addChild(container);
		parent.removeChild(container);
		parent.insertChildBefore(forEachNode, container);
		return container;
	}

	private List<TagNode> filterNestedForEachElements(TagNode baseNode, List<TagNode> forEachNodes) {
		List<TagNode> tagNodesToRemove = new ArrayList<TagNode>();
		for (TagNode tagNode : forEachNodes) {
			if (hasForEachParentBelowBase(tagNode, baseNode)) {
				tagNodesToRemove.add(tagNode);
			}
		}
		if (tagNodesToRemove.isEmpty()) {
			return forEachNodes;
		} else {
			forEachNodes = new ArrayList<TagNode>(forEachNodes);
			forEachNodes.removeAll(tagNodesToRemove);
			return forEachNodes;
		}
	}

	private boolean hasForEachParentBelowBase(TagNode tagNode, TagNode baseNode) {
		TagNode parent = tagNode.getParent();
		if (parent != null) {
			if (parent != baseNode) {
				if (!parent.getName().equals(tags.getForEachTag())) {
					return hasForEachParentBelowBase(parent, baseNode);
				} else {
					return true;
				}
			} else {
				return false;
			}
		}
		return false;
	}

	public void processCurlyBrackets(Document dataDocument, TagNode formHtml, Properties properties, String docBase) throws IOException,
		ValueInjectorException {

		XPathExpression selectExpression;
		Node dataDocAtDocBase;

		try {
			XPathFactory xPathFactory = xPathFactoryPool.getInstance();
			selectExpression = xPathFactory.newXPath().compile(docBase);
			dataDocAtDocBase = (Node) selectExpression.evaluate(dataDocument, XPathConstants.NODE);
			xPathFactoryPool.returnInstance(xPathFactory);
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
		replaceCurlyBrackets(properties, builder, dataDocument, new HashMap<String, Node>(), null);
	}

	private void replaceCurlyBrackets(Properties properties, StringBuilder builder, Node dataDocument, Map<String, Node> contextNodes, Integer contextindex)
		throws ValueInjectorException {
		StringBuffer completedText = new StringBuffer();
		Map<String, String> lookups = new HashMap<String, String>();
		Matcher matcher = CURLY_BRACKET_CONTENTS_PATTERN.matcher(builder);
		while (matcher.matches()) {
			// get brackets contents
			String group = matcher.group(1);
			String value = lookups.get(group);
			if (value == null) {
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
				lookups.put(group, value);
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
				XPathFactory xPathFactory = xPathFactoryPool.getInstance();
				expression = xPathFactory.newXPath().compile(xpath);
				xPathFactoryPool.returnInstance(xPathFactory);
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
