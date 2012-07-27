package com.rhinoforms;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class FormFlowFactory {

	private DocumentBuilderFactory documentBuilderFactory;
	private XPathFactory xPathFactory;
	
	public FormFlowFactory() {
		this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
		this.xPathFactory = XPathFactory.newInstance();
	}

	public FormFlow createFlow(String realFormFlowPath, Context jsContext) throws IOException {
		return doCreateFlow(realFormFlowPath, jsContext, null);
	}
	
	public FormFlow createFlow(String realFormFlowPath, Context jsContext, String dataDocumentString) throws IOException {
		return doCreateFlow(realFormFlowPath, jsContext, dataDocumentString);
	}
	
	private FormFlow doCreateFlow(String realFormFlowPath, Context jsContext, String dataDocumentString) throws IOException {
		
		Document dataDocument = null;
		if (dataDocumentString != null && !dataDocumentString.isEmpty()) {
			try {
				DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
				dataDocument = documentBuilder.parse(new ByteArrayInputStream(dataDocumentString.getBytes()));
			} catch (ParserConfigurationException e) {
				e.printStackTrace(); // TODO: error handling
			} catch (SAXException e) {
				e.printStackTrace();
			}
		}
		
		ScriptableObject scope = jsContext.initStandardObjects();
		FormFlow formFlow;
		if (dataDocument == null) {
			formFlow = new FormFlow(scope);
		} else {
			formFlow = new FormFlow(scope, dataDocument);
		}
		Object wrappedFormFlow = Context.javaToJS(formFlow, scope);
		ScriptableObject.putProperty(scope, "formFlow", wrappedFormFlow);
		String scriptPath = "/flow-loader.js";
		jsContext.evaluateReader(scope, new InputStreamReader(FormFlowFactory.class.getResourceAsStream(scriptPath)), scriptPath, 1, null);
		
		InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(realFormFlowPath));
		BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("loadFlow(");
		while (bufferedReader.ready()) {
			stringBuilder.append(bufferedReader.readLine());
		}
		stringBuilder.append(")");
		
		String newFlowJsExpresion = stringBuilder.toString();
		jsContext.evaluateString(scope, newFlowJsExpresion, realFormFlowPath, 1, null);
		return formFlow;
	}
	
	// Hack method
	public static void main(String[] args) throws Exception {
		Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream("<customer><name>Kai</name><driver>one</driver><driver>two</driver></customer>".getBytes()));
		XPathFactory xPathFactory = XPathFactory.newInstance();
		NodeList customerNodeList = (NodeList) xPathFactory.newXPath().compile("/customer").evaluate(document, XPathConstants.NODESET);
		System.out.println(customerNodeList.getLength());
		Node customer = customerNodeList.item(0);
		System.out.println(customer);
		NodeList nameNodeList = (NodeList) xPathFactory.newXPath().compile("name").evaluate(customer, XPathConstants.NODESET);
		System.out.println(nameNodeList.getLength());
		NodeList driverNodeList = (NodeList) xPathFactory.newXPath().compile("driver[0]").evaluate(customer, XPathConstants.NODESET);
		System.out.println(driverNodeList.getLength());
	}
	
}
