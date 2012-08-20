package com.rhinoforms;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.w3c.dom.Document;

import com.rhinoforms.serverside.InputPojo;

public class FormParserTest {

	private FormParser formParser;
	private FormFlow formFlow;
	private JSMasterScope masterScope;
	private DocumentHelper documentHelper;

	@Before
	public void setup() throws Exception {
		TestResourceLoader resourceLoader = new TestResourceLoader();
		this.formParser = new FormParser(resourceLoader);
		this.formFlow = new FormFlowFactory().createFlow("src/test/resources/test-flow1.js", Context.enter(), "<myData><fishes><fish><name>One</name></fish><fish><name>Two</name></fish></fishes></myData>");
		this.documentHelper = new DocumentHelper();
		this.formFlow.navigateToFirstForm(documentHelper);
		
		Context jsContext = Context.enter();
		try {
			this.masterScope = new RhinoFormsMasterScopeFactory().createMasterScope(jsContext, resourceLoader);
		} finally {
			Context.exit();
		}
	}

	@Test
	public void testRecuringEntityOutput() throws Exception {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		formParser.parseForm(readFileContents("src/test/resources/fishes.html"), formFlow, new PrintWriter(outputStream), masterScope);
		String actual = new String(outputStream.toByteArray());
		Assert.assertTrue(actual.contains("<span index=\"1\">One</span>"));
		Assert.assertTrue(actual.contains("<span index=\"2\">Two</span>"));
	}

	@Test
	public void testIgnoreFieldsWithNoName() throws Exception {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		formParser.parseForm(readFileContents("src/test/resources/fields-with-no-name.html"), formFlow, new PrintWriter(outputStream), masterScope);
	}

	@Test
	public void testAllInputTypes() throws Exception {
		this.formFlow = new FormFlowFactory().createFlow("src/test/resources/test-flow1.js", Context.enter(), "<myData><terms>disagree</terms><title>Miss</title><canWalkOnHands>true</canWalkOnHands></myData>");
		this.formFlow.navigateToFirstForm(documentHelper);
		
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		formParser.parseForm(readFileContents("src/test/resources/all-input-types.html"), formFlow, new PrintWriter(byteArrayOutputStream), masterScope);
		
		List<InputPojo> inputPojos = formFlow.getCurrentInputPojos();
		
		Assert.assertEquals(4, inputPojos.size());
		Assert.assertEquals("terms", inputPojos.get(0).getName());
		Assert.assertEquals("firstName", inputPojos.get(1).getName());
		Assert.assertEquals("canWalkOnHands", inputPojos.get(2).getName());
		Assert.assertEquals("title", inputPojos.get(3).getName());
		String parsedFormHtml = new String(byteArrayOutputStream.toByteArray());
		System.out.println(parsedFormHtml);
		Assert.assertTrue(parsedFormHtml.contains("type=\"radio\" name=\"terms\" value=\"disagree\" checked=\"checked\""));
		Assert.assertTrue(parsedFormHtml.contains("<option selected=\"selected\">Miss</option>"));
		Assert.assertTrue(parsedFormHtml.contains("type=\"checkbox\" name=\"canWalkOnHands\" checked=\"checked\""));
	}
	
	@Test
	public void testSelectRange() throws Exception {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		formParser.parseForm(readFileContents("src/test/resources/select-range.html"), formFlow, new PrintWriter(byteArrayOutputStream), masterScope);
		String parsedFormHtml = new String(byteArrayOutputStream.toByteArray());
		String[] split = parsedFormHtml.split("<option>\\d\\d\\d\\d");
		Assert.assertEquals(6, split.length);
	}
	
	@Test
	public void testlookupValueByFieldName() throws Exception {
		Document createDocument = createDocument("<myData><customer>One</customer></myData>");
		Assert.assertEquals("One", formParser.lookupValueByFieldName(createDocument, "customer", "/myData"));
		Assert.assertEquals(null, formParser.lookupValueByFieldName(createDocument, "customer.address", "/myData"));
		Assert.assertEquals(null, formParser.lookupValueByFieldName(createDocument, "customer.address.line1", "/myData"));
		Assert.assertEquals(null, formParser.lookupValueByFieldName(createDocument, "address.line1", "/myData/customer"));
	}
	
	private String readFileContents(String filePath) throws IOException {
		StringBuilder builder = new StringBuilder();
		BufferedReader reader = new BufferedReader(new FileReader(filePath));
		while (reader.ready()) {
			builder.append(reader.readLine());
			builder.append("\n");
		}
		return builder.toString();
	}
	
	private Document createDocument(String dataDocumentString) throws Exception {
		DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		return documentBuilder.parse(new ByteArrayInputStream(dataDocumentString.getBytes()));
	}

}
