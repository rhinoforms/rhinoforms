package com.rhinoforms;

import java.io.StringWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

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

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.rhinoforms.serverside.InputPojo;

public class DocumentManipulator {

	private XPathFactory xPathFactory;
	
	private static final Logger LOGGER = Logger.getLogger(DocumentManipulator.class);

	public DocumentManipulator() {
		this.xPathFactory = XPathFactory.newInstance();
	}

	public void persistFormData(List<InputPojo> inputPOJOs, String documentBasePath, Document dataDocument) throws DocumentManipulatorException {
		for (InputPojo inputPojo : inputPOJOs) {
			String xPathString = documentBasePath + "/" + inputPojo.name.replaceAll("\\.", "/");
			Node node = lookupOrCreateNode(dataDocument, xPathString);
			node.setTextContent(inputPojo.value);
		}
	}

	private Node lookupOrCreateNode(Document dataDocument, String xPathString) throws DocumentManipulatorException {
		try {
			XPathExpression fullXPathExpression = newXPath(xPathString);
			NodeList fullPathNodeList = lookup(dataDocument, fullXPathExpression);
			if (fullPathNodeList.getLength() == 1) {
				return fullPathNodeList.item(0);
			} else if (fullPathNodeList.getLength() > 1) {
				throw new DocumentManipulatorException("XPath matches more than one node '" + xPathString + "'.");
			} else {
				String[] xPathStringParts = xPathString.split("/");
				Stack<String> xPathPartsStack = new Stack<String>();
				Collections.addAll(xPathPartsStack, xPathStringParts);
				Collections.reverse(xPathPartsStack);
				xPathPartsStack.pop(); // discard blank string from before first slash
				
				return recursiveCreateNode(dataDocument, "", dataDocument, xPathPartsStack);
			}
		} catch (XPathExpressionException e) {
			throw new DocumentManipulatorException(e);
		}
	}

	private Node recursiveCreateNode(Document doc, String progressiveXpath, Node currentNode, Stack<String> xPathPartsStack) throws XPathExpressionException, DocumentManipulatorException {
		String nodeToFindOrCreate = xPathPartsStack.pop();
		progressiveXpath += "/" + nodeToFindOrCreate;
		NodeList nodeSet = lookup(doc, newXPath(progressiveXpath));
		Node nextNode;
		if (nodeSet.getLength() == 0) {
			LOGGER.debug("Creating node at " + progressiveXpath);
			nextNode = doc.createElement(cleanNodeName(nodeToFindOrCreate));
			currentNode.appendChild(nextNode);
		} else if (nodeSet.getLength() == 1){
			LOGGER.debug("Found node at " + progressiveXpath);
			nextNode = nodeSet.item(0);
		} else {
			throw new DocumentManipulatorException("Node list should contain one element. XPath:'" + progressiveXpath + "', node count:" + nodeSet.getLength());
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

	private NodeList lookup(Document dataDocument, XPathExpression fullXPathExpression) throws XPathExpressionException {
		return (NodeList) fullXPathExpression.evaluate(dataDocument, XPathConstants.NODESET);
	}

	private XPathExpression newXPath(String xPathString) throws XPathExpressionException {
		return xPathFactory.newXPath().compile(xPathString);
	}
	
	public String documentToString(Document document) throws TransformerException {
		StringWriter writer = new StringWriter();
		documentToWriter(document, writer);
		return writer.toString();
	}

	public void documentToWriter(Document document, Writer writer) throws TransformerException {
		TransformerFactory transFactory = TransformerFactory.newInstance();
		Transformer transformer = transFactory.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.transform(new DOMSource(document), new StreamResult(writer));
	}

}
