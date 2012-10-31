package com.rhinoforms.xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.rhinoforms.flow.InputPojo;

public class DocumentHelperTest {

	private DocumentHelper documentManipulator;
	private Document dataDocument;
	private String documentBasePath;
	private ArrayList<InputPojo> inputPOJOs;

	@Before
	public void setup() throws Exception {
		this.documentManipulator = new DocumentHelper();
		this.documentBasePath = "/myData";
		this.inputPOJOs = new ArrayList<InputPojo>();
	}

	@Test
	public void testPersistOneExistingFieldOneDeep() throws Exception {
		setDoc("<myData><name/></myData>");
		this.inputPOJOs.add(new InputPojo("name", "Kai"));
		Assert.assertEquals("<myData><name/></myData>", documentManipulator.documentToString(dataDocument));
		
		documentManipulator.persistFormData(inputPOJOs, documentBasePath, dataDocument);
		
		Assert.assertEquals("<myData><name>Kai</name></myData>", documentManipulator.documentToString(dataDocument));
	}
	
	@Test
	public void testPersistOneNewFieldOneDeep() throws Exception {
		setDoc("<myData/>");
		this.inputPOJOs.add(new InputPojo("name", "Kai"));
		Assert.assertEquals("<myData/>", documentManipulator.documentToString(dataDocument));
		
		documentManipulator.persistFormData(inputPOJOs, documentBasePath, dataDocument);
		
		Assert.assertEquals("<myData><name>Kai</name></myData>", documentManipulator.documentToString(dataDocument));
	}
	
	@Test
	public void testPersistOneNewFieldFiveDeep() throws Exception {
		setDoc("<myData/>");
		this.inputPOJOs.add(new InputPojo("session.customer.details.name.first", "Kai"));
		Assert.assertEquals("<myData/>", documentManipulator.documentToString(dataDocument));
		
		documentManipulator.persistFormData(inputPOJOs, documentBasePath, dataDocument);
		
		Assert.assertEquals("<myData><session><customer><details><name><first>Kai</first></name></details></customer></session></myData>", documentManipulator.documentToString(dataDocument));
	}
	
	@Test
	public void testPersistOneNewFieldTwoDeepTwoDeepDocRoot() throws Exception {
		setDoc("<myData/>");
		this.inputPOJOs.add(new InputPojo("name.first", "Kai"));
		Assert.assertEquals("<myData/>", documentManipulator.documentToString(dataDocument));
		
		documentManipulator.persistFormData(inputPOJOs, "/myData/session/customer/details", dataDocument);
		
		Assert.assertEquals("<myData><session><customer><details><name><first>Kai</first></name></details></customer></session></myData>", documentManipulator.documentToString(dataDocument));
	}
	
	@Test
	public void testPersistOneFirstInArray() throws Exception {
		setDoc("<myData/>");
		this.inputPOJOs.add(new InputPojo("name", "Kai"));
		Assert.assertEquals("<myData/>", documentManipulator.documentToString(dataDocument));
		
		documentManipulator.persistFormData(inputPOJOs, "/myData/customers/customer[1]", dataDocument);
		
		Assert.assertEquals("<myData><customers><customer><name>Kai</name></customer></customers></myData>", documentManipulator.documentToString(dataDocument));
	}
	
	@Test
	public void testUpdateOneInArray() throws Exception {
		String initialDoc = "<myData><customers><customer><name>Kai</name></customer></customers></myData>";
		setDoc(initialDoc);
		this.inputPOJOs.add(new InputPojo("name", "Sam"));
		Assert.assertEquals(initialDoc, documentManipulator.documentToString(dataDocument));
		
		documentManipulator.persistFormData(inputPOJOs, "/myData/customers/customer[1]", dataDocument);
		
		Assert.assertEquals("<myData><customers><customer><name>Sam</name></customer></customers></myData>", documentManipulator.documentToString(dataDocument));
	}

	@Test
	public void testPersistOneSecondInArray() throws Exception {
		String initialDoc = "<myData><customers><customer><name>Kai</name></customer></customers></myData>";
		setDoc(initialDoc);
		this.inputPOJOs.add(new InputPojo("name", "Sam"));
		Assert.assertEquals(initialDoc, documentManipulator.documentToString(dataDocument));
		
		documentManipulator.persistFormData(inputPOJOs, "/myData/customers/customer[2]", dataDocument);
		
		Assert.assertEquals("<myData><customers><customer><name>Kai</name></customer><customer><name>Sam</name></customer></customers></myData>", documentManipulator.documentToString(dataDocument));
	}
	
	@Test
	public void testUpdateSecondInArray() throws Exception {
		String initialDoc = "<myData><customers><customer><name>Kai</name></customer><customer><name>Sam</name></customer></customers></myData>";
		setDoc(initialDoc);
		this.inputPOJOs.add(new InputPojo("name", "Dan"));
		Assert.assertEquals(initialDoc, documentManipulator.documentToString(dataDocument));
		
		documentManipulator.persistFormData(inputPOJOs, "/myData/customers/customer[2]", dataDocument);
		
		Assert.assertEquals("<myData><customers><customer><name>Kai</name></customer><customer><name>Dan</name></customer></customers></myData>", documentManipulator.documentToString(dataDocument));
	}
	
	@Test
	public void testClearFormDataOtherChildrenInDoc() throws Exception {
		String initialDoc = "<myData><customers><customer><name>Kai</name></customer><customer><name>Sam</name></customer></customers></myData>";
		setDoc(initialDoc);
		this.inputPOJOs.add(new InputPojo("name", ""));
		Assert.assertEquals(initialDoc, documentManipulator.documentToString(dataDocument));
		
		documentManipulator.clearFormData(this.inputPOJOs, "/myData/customers/customer[2]", dataDocument);
		
		Assert.assertEquals("<myData><customers><customer><name>Kai</name></customer></customers></myData>", documentManipulator.documentToString(dataDocument));
	}
	
	@Test
	public void testClearFormDataOnlyThingInDoc() throws Exception {
		String initialDoc = "<myData><customers><customer><name>Sam</name></customer></customers></myData>";
		setDoc(initialDoc);
		this.inputPOJOs.add(new InputPojo("name", ""));
		Assert.assertEquals(initialDoc, documentManipulator.documentToString(dataDocument));
		
		documentManipulator.clearFormData(this.inputPOJOs, "/myData/customers/customer", dataDocument);
		
		Assert.assertEquals("<myData/>", documentManipulator.documentToString(dataDocument));
	}
	
	@Test
	public void testResolveXpathIndexesForAction_oneNumbered() throws Exception {
		String initialDoc = "<myData><customers><customer><name>Kai</name></customer><customer><name>Sam</name></customer></customers></myData>";
		setDoc(initialDoc);
		HashMap<String, String> actionParams = new HashMap<String, String>();
		actionParams.put("indexA", "2");
		
		String resolvedXPath = documentManipulator.resolveXPathIndexesForAction("/myData/customers/customer[indexA]", actionParams, dataDocument);
		
		Assert.assertEquals("/myData/customers/customer[2]", resolvedXPath);
	}
	
	@Test
	public void testResolveXpathIndexesForAction_twoNumbered() throws Exception {
		String initialDoc = "<myData><customers><customer><name>Kai</name></customer><customer><name>Sam</name></customer></customers></myData>";
		setDoc(initialDoc);
		HashMap<String, String> actionParams = new HashMap<String, String>();
		actionParams.put("indexA", "1");
		actionParams.put("indexB", "2");
		
		String resolvedXPath = documentManipulator.resolveXPathIndexesForAction("/myData/customers/customer[indexA]/address[indexB]", actionParams, dataDocument);
		
		Assert.assertEquals("/myData/customers/customer[1]/address[2]", resolvedXPath);
	}
	
	@Test
	public void testResolveXpathIndexesForAction_oneNamed() throws Exception {
		String initialDoc = "<myData><customers><customer><name>Kai</name></customer><customer><name>Sam</name></customer></customers></myData>";
		setDoc(initialDoc);
		HashMap<String, String> actionParams = new HashMap<String, String>();
		actionParams.put("index", "next");
		
		String resolvedXPath = documentManipulator.resolveXPathIndexesForAction("/myData/customers/customer[index]", actionParams, dataDocument);
		
		Assert.assertEquals("/myData/customers/customer[3]", resolvedXPath);
	}
	
	private void setDoc(String dataDocumentString) throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		dataDocument = documentBuilder.parse(new ByteArrayInputStream(dataDocumentString.getBytes()));
	}

}
