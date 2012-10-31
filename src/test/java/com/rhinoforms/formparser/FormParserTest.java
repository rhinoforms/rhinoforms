package com.rhinoforms.formparser;

import static com.rhinoforms.TestUtil.createDocument;
import static com.rhinoforms.TestUtil.serialiseHtmlCleanerNode;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.List;

import junit.framework.Assert;

import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.w3c.dom.Document;

import com.rhinoforms.RhinoformsProperties;
import com.rhinoforms.TestResourceLoader;
import com.rhinoforms.flow.FormFlow;
import com.rhinoforms.flow.FormFlowFactory;
import com.rhinoforms.flow.InputPojo;
import com.rhinoforms.js.JSMasterScope;
import com.rhinoforms.js.RhinoFormsMasterScopeFactory;
import com.rhinoforms.resourceloader.ResourceLoader;
import com.rhinoforms.resourceloader.ResourceLoaderImpl;
import com.rhinoforms.xml.DocumentHelper;

public class FormParserTest {

	private FormParser formParser;
	private FormFlow formFlow;
	private JSMasterScope masterScope;
	private DocumentHelper documentHelper;
	private HtmlCleaner htmlCleaner;
	private FormFlowFactory formFlowFactory;
	private ResourceLoader resourceLoader;

	@Before
	public void setup() throws Exception {
		Context jsContext = Context.enter();
		this.resourceLoader = new ResourceLoaderImpl(new TestResourceLoader(), new TestResourceLoader());
		this.formParser = new FormParser(resourceLoader);
		this.documentHelper = new DocumentHelper();
		this.htmlCleaner = new HtmlCleaner();
		
		this.masterScope = new RhinoFormsMasterScopeFactory().createMasterScope(jsContext, resourceLoader);
		this.formFlowFactory = new FormFlowFactory(this.resourceLoader, this.masterScope);
		this.formFlow = formFlowFactory.createFlow("test-flow1.js", "<myData><fishes><fish><name>One</name></fish><fish><name>Two</name></fish></fishes></myData>");
		this.formFlow.navigateToFirstForm(documentHelper);
	}

	@After
	public void after() {
		Context.exit();
	}
	
	@Test
	public void testIgnoreFieldsWithNoName() throws Exception {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		formParser.parseForm(new FileInputStream("src/test/resources/fields-with-no-name.html"), formFlow, new PrintWriter(outputStream), masterScope, false);
	}

	@Test
	public void testAllInputTypes() throws Exception {
		this.formFlow = formFlowFactory.createFlow("test-flow1.js", "<myData><terms>disagree</terms><title>Miss</title><canWalkOnHands>true</canWalkOnHands></myData>");
		this.formFlow.navigateToFirstForm(documentHelper);
		
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		formParser.parseForm(new FileInputStream("src/test/resources/all-input-types.html"), formFlow, new PrintWriter(byteArrayOutputStream), masterScope, false);
		
		List<InputPojo> inputPojos = formFlow.getCurrentInputPojos();
		
		Assert.assertEquals(5, inputPojos.size());
		Assert.assertEquals("terms", inputPojos.get(0).getName());
		Assert.assertEquals("firstName", inputPojos.get(1).getName());
		Assert.assertEquals("canWalkOnHands", inputPojos.get(2).getName());
		Assert.assertEquals("title", inputPojos.get(3).getName());
		Assert.assertEquals("maritalStatusCode", inputPojos.get(4).getName());
		String parsedFormHtml = new String(byteArrayOutputStream.toByteArray());
		Assert.assertTrue(parsedFormHtml.contains("type=\"radio\" name=\"terms\" value=\"disagree\" checked=\"checked\""));
		Assert.assertTrue(parsedFormHtml.contains("<option selected=\"selected\">Miss</option>"));
		Assert.assertTrue(parsedFormHtml.contains("type=\"checkbox\" name=\"canWalkOnHands\" checked=\"checked\""));
	}
	
	@Test
	public void testSelectFromCSV() throws Exception {
		this.formFlow = formFlowFactory.createFlow("test-flow1.js", null);
		this.formFlow.navigateToFirstForm(documentHelper);
		
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		formParser.parseForm(new FileInputStream("src/test/resources/all-input-types.html"), formFlow, new PrintWriter(byteArrayOutputStream), masterScope, false);
		
		String parsedFormHtml = new String(byteArrayOutputStream.toByteArray());
		Assert.assertTrue("Option should have value and label from CSV.", parsedFormHtml.contains("<option value=\"1\">Single</option>"));
	}
	
	@Test
	public void testSelectFromCSVWithSelectedValue() throws Exception {
		this.formFlow = formFlowFactory.createFlow("test-flow1.js", "<myData><maritalStatusCode>2</maritalStatusCode></myData>");
		this.formFlow.navigateToFirstForm(documentHelper);
		
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		formParser.parseForm(new FileInputStream("src/test/resources/all-input-types.html"), formFlow, new PrintWriter(byteArrayOutputStream), masterScope, false);
		
		String parsedFormHtml = new String(byteArrayOutputStream.toByteArray());
		Assert.assertTrue("Option should have value and label from CSV.", parsedFormHtml.contains("<option value=\"1\">Single</option>"));
		Assert.assertTrue("Option 2 should be selected.", parsedFormHtml.contains("<option value=\"2\" selected=\"selected\">Married</option>"));
	}
	
	@Test
	public void testSelectRangePreselectFirstOptionDefaultFalse() throws Exception {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		formParser.parseForm(new FileInputStream("src/test/resources/select-range.html"), formFlow, new PrintWriter(byteArrayOutputStream), masterScope, false);
		String parsedFormHtml = new String(byteArrayOutputStream.toByteArray());
		String[] split = parsedFormHtml.split("<option[^<]*");
		Assert.assertEquals(8, split.length);
		Assert.assertTrue(parsedFormHtml.contains("<option value=\"\">-- Please Select --</option>"));
	}
	
	@Test
	public void testSelectRangePreselectFirstOptionTrue() throws Exception {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		formParser.parseForm(new FileInputStream("src/test/resources/select-range-preselect-true.html"), formFlow, new PrintWriter(byteArrayOutputStream), masterScope, false);
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
		formParser.processIncludes(html, formFlow);

		// Post-assertions
		String processedHtml = serialiseHtmlCleanerNode(html);
		Assert.assertTrue("firstName input has now been included", html.findElementByAttValue("name", "firstName", true, true) != null);
		Assert.assertTrue("canWalkOnHands input has now been included", html.findElementByAttValue("name", "canWalkOnHands", true, true) != null);
		Assert.assertTrue("Next button is still there", html.findElementByAttValue("action", "next", true, true) != null);
		
		int nameIndex = processedHtml.indexOf("name=\"firstName\"");
		int handsIndex = processedHtml.indexOf("name=\"canWalkOnHands\"");
		Assert.assertTrue("firstName element comes before canWalkOnHands element in the html", nameIndex < handsIndex);
	}
	
	@Test
	public void testDebugBar() throws Exception {
		RhinoformsProperties.getInstance().setShowDebugBar(true);
		this.formParser = new FormParser(resourceLoader);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		formParser.parseForm(new FileInputStream("src/test/resources/empty-form.html"), formFlow, new PrintWriter(outputStream), masterScope, false);
		String string = outputStream.toString();
		System.out.println(string);
		Assert.assertTrue(string.contains("<div class=\"rf-debugbar\">"));
	}

}
