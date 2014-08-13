package com.rhinoforms.xml;

import com.rhinoforms.flow.InputPojo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocumentHelper {

	private XPathFactory xPathFactory;
	private DocumentBuilder documentBuilder;

	final Logger logger = LoggerFactory.getLogger(DocumentHelper.class);

	public DocumentHelper() {
		this.xPathFactory = XPathFactory.newInstance();
		try {
			documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
	}

	public void persistFormData(List<InputPojo> inputPOJOs, String docBase, Document dataDocument) throws FlowExceptionXPath {
		for (InputPojo inputPojo : inputPOJOs) {
			String xPathString = getXPathStringForInput(docBase, inputPojo);
			Node node = lookupOrCreateNode(dataDocument, xPathString);
			node.setTextContent(inputPojo.getValue());
		}
	}

	public void clearFormData(List<InputPojo> inputsToClear, String docBase, Document dataDocument) throws FlowExceptionXPath {
		String xPathString = null;
		try {
			for (InputPojo inputPojo : inputsToClear) {
				xPathString = getXPathStringForInput(docBase, inputPojo);
				XPathExpression xPath = newXPath(xPathString);
				NodeList nodeList = lookup(dataDocument, xPath);
				if (nodeList.getLength() > 0) {
					Node item = nodeList.item(0);
					Node parentNode = item.getParentNode();
					parentNode.removeChild(item);
					deleteNodeIfEmptyRecurseUp(parentNode);
				}
			}
		} catch (XPathExpressionException e) {
			throw new FlowExceptionXPath("Problem with generated XPath '" + xPathString + "'.", e);
		}
	}

	private void deleteNodeIfEmptyRecurseUp(Node node) {
		Node parentNode = node.getParentNode();
		if (!node.hasChildNodes() && parentNode != null && parentNode.getParentNode() != null) {
			parentNode.removeChild(node);
			deleteNodeIfEmptyRecurseUp(parentNode);
		}
	}

	private String getXPathStringForInput(String documentBasePath, InputPojo inputPojo) {
		return documentBasePath + "/" + inputPojo.getName().replaceAll("\\.", "/");
	}

	public String resolveXPathIndexesForAction(String actionName, String xpath, Map<String, String> actionParams, Document document)
			throws FlowExceptionXPath {
		Pattern xpathIndexPattern = Pattern.compile("(.+?)\\[([^0-9]+?)\\].*");
		Matcher matcher = xpathIndexPattern.matcher(xpath);
		if (matcher.matches()) {
			String group = matcher.group(matcher.groupCount());
			if (group.equals("next")) {
				String xpathToCount = xpath.substring(0, xpath.indexOf("[next]"));
				try {
					XPathExpression xpathToCountExpression = newXPath(xpathToCount);
					NodeList list = lookup(document, xpathToCountExpression);
					int listLength = list.getLength();
					xpath = xpath.replace("[next]", "[" + ++listLength + "]");
				} catch (XPathExpressionException e) {
					throw new FlowExceptionXPath("Problem with action named '" + actionName + "'. Attempting to resolve 'next' alias but can not compile or evaluate XPath '" + xpathToCount + "'.", e);
				}
				return resolveXPathIndexesForAction(actionName, xpath, actionParams, document);
			} else if (actionParams.containsKey(group)) {
				xpath = xpath.replace("[" + group + "]", "[" + actionParams.get(group) + "]");
				return resolveXPathIndexesForAction(actionName, xpath, actionParams, document);
			} else {
				throw new FlowExceptionXPath("Problem with action named '" + actionName + "'. Index alias is not recognised. Index alias:'" + group + "', XPath:'" + xpath
						+ "', action params:'" + actionParams + "'");
			}
		} else {
			return xpath;
		}
	}

	public Node createElementIfNotThere(Document dataDocument, String xPathString) throws FlowExceptionXPath {
		return lookupOrCreateNode(dataDocument, xPathString);
	}

	public Node lookupOrCreateNode(Document dataDocument, String xPathString) throws FlowExceptionXPath {
		try {
			XPathExpression fullXPathExpression = newXPath(xPathString);
			NodeList fullPathNodeList = lookup(dataDocument, fullXPathExpression);
			if (fullPathNodeList.getLength() == 1) {
				return fullPathNodeList.item(0);
			} else if (fullPathNodeList.getLength() > 1) {
				throw new FlowExceptionXPath("XPath matches more than one node '" + xPathString + "'.");
			} else {
				String[] xPathStringParts = xPathString.split("/");
				Stack<String> xPathPartsStack = new Stack<>();
				Collections.addAll(xPathPartsStack, xPathStringParts);
				Collections.reverse(xPathPartsStack);
				xPathPartsStack.pop(); // discard blank string from before first
										// slash

				return recursiveCreateNode(dataDocument, "", dataDocument, xPathPartsStack);
			}
		} catch (XPathExpressionException e) {
			throw new FlowExceptionXPath("Problem with XPath expression.", e);
		}
	}

	private Node recursiveCreateNode(Document doc, String progressiveXpath, Node currentNode, Stack<String> xPathPartsStack)
			throws XPathExpressionException, FlowExceptionXPath {
		String nodeToFindOrCreate = xPathPartsStack.pop();
		progressiveXpath += "/" + nodeToFindOrCreate;
		NodeList nodeSet = lookup(doc, newXPath(progressiveXpath));
		Node nextNode;
		if (nodeSet.getLength() == 0) {
			logger.debug("Creating node at {}", progressiveXpath);
			nextNode = doc.createElement(cleanNodeName(nodeToFindOrCreate));
			currentNode.appendChild(nextNode);
		} else if (nodeSet.getLength() == 1) {
			logger.debug("Found node at {}", progressiveXpath);
			nextNode = nodeSet.item(0);
		} else {
			throw new FlowExceptionXPath("Node list should contain one element. XPath:'" + progressiveXpath + "', node count:"
					+ nodeSet.getLength());
		}
		if (!xPathPartsStack.isEmpty()) {
			return recursiveCreateNode(doc, progressiveXpath, nextNode, xPathPartsStack);
		} else {
			return nextNode;
		}
	}

	private String cleanNodeName(String nodeName) {
		int indexOfBracket = nodeName.indexOf("[");
		if (indexOfBracket != -1) {
			return nodeName.substring(0, indexOfBracket);
		} else {
			return nodeName;
		}
	}

	public NodeList lookup(Document dataDocument, String xPathExpression) throws XPathExpressionException {
		return lookup(dataDocument, newXPath(xPathExpression));
	}

	private NodeList lookup(Document dataDocument, XPathExpression fullXPathExpression) throws XPathExpressionException {
		return (NodeList) fullXPathExpression.evaluate(dataDocument, XPathConstants.NODESET);
	}

	private XPathExpression newXPath(String xPathString) throws XPathExpressionException {
		return xPathFactory.newXPath().compile(xPathString);
	}

	public String documentToString(Node document) throws TransformerException {
		StringWriter writer = new StringWriter();
		documentToWriter(document, writer);
		return writer.toString();
	}

	public void documentToWriter(Node document, Writer writer) throws TransformerException {
		documentToWriter(document, writer, false, true);
	}

	public void documentToWriter(Node document, Writer writer, boolean omitXmlDeclaration) throws TransformerException {
		documentToWriter(document, writer, false, omitXmlDeclaration);
	}

	public void documentToWriterPretty(Node document, Writer writer) throws TransformerException {
		documentToWriter(document, writer, true, true);
	}

	public void documentToWriterPretty(Node document, Writer writer, boolean omitXmlDeclaration) throws TransformerException {
		documentToWriter(document, writer, true, omitXmlDeclaration);
	}

	private void documentToWriter(Node document, Writer writer, boolean indent, boolean omitXmlDeclaration) throws TransformerException {
		TransformerFactory transFactory = TransformerFactory.newInstance();
		Transformer transformer = transFactory.newTransformer();
		if (omitXmlDeclaration) {
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		}
		if (indent) {
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		}
		transformer.transform(new DOMSource(document), new StreamResult(writer));
	}

	public void deleteElements(String xpath, Document dataDocument) throws XPathExpressionException {
		XPathExpression expression = newXPath(xpath);
		NodeList nodesToDelete = (NodeList) expression.evaluate(dataDocument, XPathConstants.NODESET);
		for (int i = 0; i < nodesToDelete.getLength(); i++) {
			Node nodeToDelete = nodesToDelete.item(i);
			nodeToDelete.getParentNode().removeChild(nodeToDelete);
		}
	}

	public void deleteElementIfEmptyRecurseUp(Document dataDocument, String xpath) throws XPathExpressionException {
		XPathExpression expression = newXPath(xpath);
		Node nodeToDelete = (Node) expression.evaluate(dataDocument, XPathConstants.NODE);
		if (nodeToDelete != null) {
			deleteNodeIfEmptyRecurseUp(nodeToDelete);
		}
	}

	public Document streamToDocument(InputStream inputStream) throws DocumentHelperException {
		try {
			return documentBuilder.parse(inputStream);
		} catch (Exception e) {
			throw new DocumentHelperException("Failed to parse input stream.", e);
		}
	}
	
	public Document stringToDocument(String inputString) throws DocumentHelperException {
		return streamToDocument(new ByteArrayInputStream(inputString.getBytes()));
	}

	public Document newDocument() {
		return documentBuilder.newDocument();
	}

}
