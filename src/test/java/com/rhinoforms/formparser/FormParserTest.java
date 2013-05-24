package com.rhinoforms.formparser;

import static com.rhinoforms.TestUtil.createDocument;
import static com.rhinoforms.TestUtil.serialiseHtmlCleanerNode;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.w3c.dom.Document;

import com.rhinoforms.TestApplicationContext;
import com.rhinoforms.flow.FormFlow;
import com.rhinoforms.flow.FormFlowFactory;
import com.rhinoforms.flow.InputPojo;
import com.rhinoforms.flow.SubmissionTimeKeeper;
import com.rhinoforms.js.JSMasterScope;
import com.rhinoforms.xml.DocumentHelper;

public class FormParserTest {

	private FormParser formParser;
	private FormFlow formFlow;
	private JSMasterScope masterScope;
	private DocumentHelper documentHelper;
	private HtmlCleaner htmlCleaner;
	private FormFlowFactory formFlowFactory;
	private SubmissionTimeKeeper submissionTimeKeeper;

	public FormParserTest() throws Exception {
		Context.enter();
		TestApplicationContext applicationContext = new TestApplicationContext();
		submissionTimeKeeper = applicationContext.getSubmissionTimeKeeper();
		this.formParser = applicationContext.getFormParser();
		this.documentHelper = applicationContext.getDocumentHelper();
		this.htmlCleaner = applicationContext.getHtmlCleaner();
		this.masterScope = applicationContext.getMasterScope();
		this.formFlowFactory = applicationContext.getFormFlowFactory();
	}
	
	@Before
	public void setup() throws Exception {
		this.formFlow = formFlowFactory.createFlow("test-flow1.js", "<myData><fishes><fish><name>One</name></fish><fish><name>Two</name></fish></fishes></myData>");
		this.formFlow.navigateToFirstForm(documentHelper);
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
		
		Assert.assertEquals(6, inputPojos.size());
		Assert.assertEquals("terms", inputPojos.get(0).getName());
		Assert.assertEquals("firstName", inputPojos.get(1).getName());
		Assert.assertEquals("canWalkOnHands", inputPojos.get(2).getName());
		Assert.assertEquals("title", inputPojos.get(3).getName());
		Assert.assertEquals("maritalStatusCode", inputPojos.get(4).getName());
		Assert.assertEquals("additionalInfo", inputPojos.get(5).getName());
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
	public void testActionEstimate() throws Exception {
		ArrayList<Integer> times = new ArrayList<Integer>();
		times.add(5133);
		submissionTimeKeeper.recordTimeTaken("one", "next", times);
		
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		formParser.parseForm(new FileInputStream("src/test/resources/all-input-types.html"), formFlow, new PrintWriter(byteArrayOutputStream), masterScope, false);
		
		String parsedFormHtml = new String(byteArrayOutputStream.toByteArray());
		String submitInput = grep("submit", parsedFormHtml);
		Assert.assertEquals(" <input type=\"submit\" rf.action=\"next\" value=\"Next\" rf.actiontype=\"next\" rf.actiontimeestimate=\"[5133]\" />", submitInput);
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
	public void testSelectOptionsForLoop() throws Exception {
		this.formFlow = formFlowFactory.createFlow("test-flow1.js", "<myData><years><year>2000</year><year>2010</year><year>2020</year></years></myData>");
		this.formFlow.navigateToFirstForm(documentHelper);

		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		formParser.parseForm(new FileInputStream("src/test/resources/select-options-for-loop.html"), formFlow, new PrintWriter(byteArrayOutputStream), masterScope, false);
		String parsedFormHtml = new String(byteArrayOutputStream.toByteArray());
		Assert.assertTrue(parsedFormHtml.contains("<select name=\"year\"><option>2000</option><option>2010</option><option>2020</option></select>"));
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
	public void testPerpetuateIncludeIfStatementsToInputs() throws Exception {
		this.formFlow = formFlowFactory.createFlow("test-flow1.js", "<myData><terms>disagree</terms><title>Miss</title><canWalkOnHands>true</canWalkOnHands></myData>");
		this.formFlow.navigateToFirstForm(documentHelper);
		
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		formParser.parseForm(new FileInputStream("src/test/resources/perpetuate-includeIf-test.html"), formFlow, new PrintWriter(byteArrayOutputStream), masterScope, false);
		
		List<InputPojo> inputPojos = formFlow.getCurrentInputPojos();
		
		Assert.assertEquals(5, inputPojos.size());
		
		InputPojo terms = inputPojos.get(0);
		Assert.assertEquals("terms", terms.getName());
		Assert.assertEquals("{ false }", terms.getRfAttributes().get("rf.includeif"));
		
		InputPojo title = inputPojos.get(3);
		Assert.assertEquals("title", title.getName());
		Assert.assertEquals("{ true }", title.getRfAttributes().get("rf.includeif"));
		
		InputPojo firstName = inputPojos.get(1);
		Assert.assertEquals("firstName", firstName.getName());
		Assert.assertEquals("{ true }", firstName.getRfAttributes().get("rf.includeif"));
		
		InputPojo canWalkOnHands = inputPojos.get(2);
		Assert.assertEquals("canWalkOnHands", canWalkOnHands.getName());
		Assert.assertEquals(null, canWalkOnHands.getRfAttributes().get("rf.includeif"));

		InputPojo textarea = inputPojos.get(4);
		Assert.assertEquals("textarea", textarea.getName());
		Assert.assertEquals("{ false }", textarea.getRfAttributes().get("rf.includeif"));
	}
	
	@Test
	public void testDebugBar() throws Exception {
		this.formParser.setShowDebugBar(true);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		formParser.parseForm(new FileInputStream("src/test/resources/empty-form.html"), formFlow, new PrintWriter(outputStream), masterScope, false);
		String string = outputStream.toString();
		Assert.assertTrue(string.contains("<div class=\"rf-debugbar\">"));
	}
	
	@Test
	public void testAmpPound() throws Exception {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		formParser.parseForm(getClass().getResourceAsStream("amp.html"), formFlow, new PrintWriter(outputStream), masterScope, false);
		String string = outputStream.toString();
		Assert.assertTrue(string.contains("<span>&amp;</span>"));
		Assert.assertTrue(string.contains("<span>&pound;</span>"));
	}
	
	@Test
	public void testIncludeWithForLoop() throws Exception {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		formParser.parseForm(new FileInputStream("src/test/resources/include-with-forloop-test/include-with-forloop-test.html"), formFlow, new PrintWriter(outputStream), masterScope, false);
		String html = outputStream.toString();
		Assert.assertTrue(html.contains("<p>One</p>"));
		Assert.assertFalse(html.contains("forEach"));
	}

	@Test
	public void testFormIdClassSet() throws Exception {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		formParser.parseForm(new FileInputStream("src/test/resources/empty-form.html"), formFlow, new PrintWriter(outputStream), masterScope, false);
		String string = outputStream.toString();
		Assert.assertTrue(string.contains("<form rhinoforms=\"true\" parsed=\"true\" class=\"one\">"));
	}
	
	@Test
	public void testFormIdClassAdded() throws Exception {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		formParser.parseForm(new FileInputStream("src/test/resources/empty-form-with-class.html"), formFlow, new PrintWriter(outputStream), masterScope, false);
		String string = outputStream.toString();
		Assert.assertTrue(string.contains("<form rhinoforms=\"true\" class=\"myForm one\" parsed=\"true\">"));
	}
	
	private String grep(String string, String parsedFormHtml) throws IOException {
		BufferedReader bufferedReader = new BufferedReader(new StringReader(parsedFormHtml));
		String line;
		while ((line = bufferedReader.readLine()) != null) {
			if (line.contains(string)) {
				return line;
			}
		}
		return null;
	}
	
}
