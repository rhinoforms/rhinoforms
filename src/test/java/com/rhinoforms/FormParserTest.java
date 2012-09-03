package com.rhinoforms;

import static com.rhinoforms.TestUtil.createDocument;
import static com.rhinoforms.TestUtil.readFileContents;
import static com.rhinoforms.TestUtil.serialiseNode;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.List;

import junit.framework.Assert;

import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
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
	private HtmlCleaner htmlCleaner;

	@Before
	public void setup() throws Exception {
		TestResourceLoader resourceLoader = new TestResourceLoader();
		this.formParser = new FormParser(resourceLoader);
		this.formFlow = new FormFlowFactory().createFlow("src/test/resources/test-flow1.js", Context.enter(), "<myData><fishes><fish><name>One</name></fish><fish><name>Two</name></fish></fishes></myData>");
		this.documentHelper = new DocumentHelper();
		this.formFlow.navigateToFirstForm(documentHelper);
		this.htmlCleaner = new HtmlCleaner();
		
		Context jsContext = Context.enter();
		try {
			this.masterScope = new RhinoFormsMasterScopeFactory().createMasterScope(jsContext, resourceLoader);
		} finally {
			Context.exit();
		}
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
		Assert.assertTrue(parsedFormHtml.contains("type=\"radio\" name=\"terms\" value=\"disagree\" checked=\"checked\""));
		Assert.assertTrue(parsedFormHtml.contains("<option selected=\"selected\">Miss</option>"));
		Assert.assertTrue(parsedFormHtml.contains("type=\"checkbox\" name=\"canWalkOnHands\" checked=\"checked\""));
	}
	
	@Test
	public void testSelectRangePreselectFirstOptionDefaultFalse() throws Exception {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		formParser.parseForm(readFileContents("src/test/resources/select-range.html"), formFlow, new PrintWriter(byteArrayOutputStream), masterScope);
		String parsedFormHtml = new String(byteArrayOutputStream.toByteArray());
		String[] split = parsedFormHtml.split("<option[^<]*");
		Assert.assertEquals(8, split.length);
		System.out.println(parsedFormHtml);
		Assert.assertTrue(parsedFormHtml.contains("<option value=\"\">-- Please Select --</option>"));
	}
	
	@Test
	public void testSelectRangePreselectFirstOptionTrue() throws Exception {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		formParser.parseForm(readFileContents("src/test/resources/select-range-preselect-true.html"), formFlow, new PrintWriter(byteArrayOutputStream), masterScope);
		String parsedFormHtml = new String(byteArrayOutputStream.toByteArray());
		String[] split = parsedFormHtml.split("<option[^<]*");
		Assert.assertEquals(7, split.length);
		Assert.assertFalse(parsedFormHtml.contains("Please Select"));
	}
	
	@Test
	public void testlookupValueByFieldName() throws Exception {
		Document createDocument = createDocument("<myData><customer>One</customer></myData>");
		Assert.assertEquals("One", formParser.lookupValueByFieldName(createDocument, "customer", "/myData"));
		Assert.assertEquals(null, formParser.lookupValueByFieldName(createDocument, "customer.address", "/myData"));
		Assert.assertEquals(null, formParser.lookupValueByFieldName(createDocument, "customer.address.line1", "/myData"));
		Assert.assertEquals(null, formParser.lookupValueByFieldName(createDocument, "address.line1", "/myData/customer"));
	}
	
	@Test
	public void testProcessIncludes() throws Exception {
		InputStream resourceAsStream = new FileInputStream("src/test/resources/include-test.html");
		TagNode html = htmlCleaner.clean(resourceAsStream);
		
		// Pre-assertions
		Assert.assertTrue("firstName input does not yet exist", html.findElementByAttValue("name", "firstName", true, true) == null);
		Assert.assertTrue("canWalkOnHands input does not yet exist", html.findElementByAttValue("name", "canWalkOnHands", true, true) == null);
		Assert.assertTrue("Next button already exists", html.findElementByAttValue("action", "next", true, true) != null);
		
		// Run method we are testing
		formParser.processIncludes(html);

		// Post-assertions
		String processedHtml = serialiseNode(html);
		Assert.assertTrue("firstName input has now been included", html.findElementByAttValue("name", "firstName", true, true) != null);
		Assert.assertTrue("canWalkOnHands input has now been included", html.findElementByAttValue("name", "canWalkOnHands", true, true) != null);
		Assert.assertTrue("Next button is still there", html.findElementByAttValue("action", "next", true, true) != null);
		
		int nameIndex = processedHtml.indexOf("name=\"firstName\"");
		int handsIndex = processedHtml.indexOf("name=\"canWalkOnHands\"");
		Assert.assertTrue("firstName element comes before canWalkOnHands element in the html", nameIndex < handsIndex);
	}

}
