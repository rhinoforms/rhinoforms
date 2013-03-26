package com.rhinoforms.formparser;

import static com.rhinoforms.TestUtil.createDocument;
import static com.rhinoforms.TestUtil.serialiseHtmlCleanerNode;

import java.io.FileInputStream;
import java.io.StringReader;
import java.util.Properties;

import junit.framework.Assert;

import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import com.rhinoforms.flow.FormFlow;

public class ValueInjectorTest {

	private ValueInjector valueInjector;
	private HtmlCleaner htmlCleaner;
	private TagNode formHtml;
	private Document dataDocument;
	private FormFlow formFlow;

	@Before
	public void setup() throws Exception {
		htmlCleaner = new HtmlCleaner();
		formHtml = htmlCleaner.clean(new FileInputStream("src/test/resources/fishes.html"));
		dataDocument = createDocument("<myData><ocean><name>Pacific</name><fishes><fish><name>One</name></fish><fish><name>Two</name></fish></fishes></ocean></myData>");

		this.valueInjector = new ValueInjector();
		formFlow = new FormFlow();
		formFlow.setProperties(new Properties());
	}

	@Test
	public void testForEachWithDocBaseRelativeXPath() throws Exception {
		valueInjector.processForEachStatements(formFlow, formHtml, dataDocument, "/myData/ocean");

		String actual = serialiseHtmlCleanerNode(formHtml);
		Assert.assertFalse(actual.contains("foreach"));
		Assert.assertTrue(actual.contains("<span index=\"1\">One</span>"));
		Assert.assertTrue(actual.contains("<span index=\"2\">Two</span>"));
	}
	
	@Test
	public void testForEachWithDocBaseRelativeXPathTextNodes() throws Exception {
		formHtml = htmlCleaner.clean(new FileInputStream("src/test/resources/fishes-text-nodes.html"));
		dataDocument = createDocument("<myData><fishes><fish_one>One</fish_one><fish_two>Two</fish_two><fish_three/></fishes></myData>");
		valueInjector.processForEachStatements(formFlow, formHtml, dataDocument, "/myData");

		String actual = serialiseHtmlCleanerNode(formHtml);
		Assert.assertFalse(actual.contains("foreach"));
		Assert.assertTrue(actual.contains("<span>One</span>"));
		Assert.assertTrue(actual.contains("<span>Two</span>"));
		String expected = "<html><head></head><body><div class=\"before\">\n" +
			" <span>One</span>\n" +
			" <span>Two</span>\n" +
			" <span>{{aFish}}</span>\n" +
			"\n" +
			"<div class=\"after\"></div></div></body></html>";
		Assert.assertEquals(expected, actual);
	}
	
	@Test
	public void testForEachWithAbsoluteXPath() throws Exception {
		formHtml = htmlCleaner.clean(new FileInputStream("src/test/resources/forEach-absolute-xpath.html"));
		TagNode forEachNode = formHtml.findElementByName("rf.forEach", true);
		String selectStatement = forEachNode.getAttributeByName("select");
		Assert.assertEquals("Select statement is absolute XPath", "/myData/ocean/fishes/fish", selectStatement);
		
		valueInjector.processForEachStatements(formFlow, formHtml, dataDocument, "/myData");

		String actual = serialiseHtmlCleanerNode(formHtml);
		Assert.assertFalse(actual.contains("foreach"));
		Assert.assertTrue(actual.contains("<span index=\"1\">One</span>"));
		Assert.assertTrue(actual.contains("<span index=\"2\">Two</span>"));
	}

	@Test
	public void testForEachWithSearchXPath() throws Exception {
		formHtml = htmlCleaner.clean(new FileInputStream("src/test/resources/forEach-search-xpath.html"));
		TagNode forEachNode = formHtml.findElementByName("rf.forEach", true);
		String selectStatement = forEachNode.getAttributeByName("select");
		Assert.assertEquals("Select statement is search XPath", "//fish", selectStatement);

		valueInjector.processForEachStatements(formFlow, formHtml, dataDocument, "/myData");

		String actual = serialiseHtmlCleanerNode(formHtml);
		Assert.assertFalse(actual.contains("foreach"));
		Assert.assertTrue(actual.contains("<span index=\"1\">One</span>"));
		Assert.assertTrue(actual.contains("<span index=\"2\">Two</span>"));
	}

	@Test
	public void testProcessRemainingCurlyBrackets() throws Exception {
		Assert.assertTrue("Placeholder present.", serialiseHtmlCleanerNode(formHtml).contains("<span class=\"ocean\">{{name}}</span>"));

		valueInjector.processRemainingCurlyBrackets(formFlow, formHtml, dataDocument, "/myData/ocean");

		String actual = serialiseHtmlCleanerNode(formHtml);
		Assert.assertTrue("Placeholder replaced with dataDocument content.", actual.contains("<span class=\"ocean\">Pacific</span>"));
		Assert.assertTrue(actual.contains("Fish count: 2"));
		Assert.assertTrue(actual.contains("First fish: One"));
	}
	
	@Test
	public void testProcessRemainingCurlyBracketsSomeProperties() throws Exception {
		formHtml = htmlCleaner.clean(new StringReader("<span class=\"ocean\">{{$someUrl}}{{name}}</span>"));
		Assert.assertTrue("Placeholder present.", serialiseHtmlCleanerNode(formHtml).contains("<span class=\"ocean\">{{$someUrl}}{{name}}</span>"));
		formFlow.getProperties().put("someUrl", "http://en.wikipedia.org/wiki/");
		
		valueInjector.processRemainingCurlyBrackets(formFlow, formHtml, dataDocument, "/myData/ocean");

		String actual = serialiseHtmlCleanerNode(formHtml);
		Assert.assertTrue("Placeholder replaced with dataDocument content.", actual.contains("<span class=\"ocean\">http://en.wikipedia.org/wiki/Pacific</span>"));
	}
	
	@Test
	public void testStringBuilderToNode() throws Exception {
		String html = "<div><span>one</span><span>two</span></div>";
		TagNode node = valueInjector.stringBuilderToNode(new StringBuilder(html));
		String actual = serialiseHtmlCleanerNode(node);
		Assert.assertEquals(html, actual);
	}
	
	@Test
	public void testStringBuilderBodyToNode() throws Exception {
		String html = "<body><span>one</span><span>two</span></body>";
		TagNode node = valueInjector.stringBuilderBodyToNode(new StringBuilder(html));
		String actual = serialiseHtmlCleanerNode(node);
		Assert.assertEquals(html, actual);
	}
	
	@Test
	public void testNodeToStringBuilder() throws Exception {
		String html = "<div><span>one</span><span>two</span></div>";
		TagNode node = valueInjector.stringBuilderToNode(new StringBuilder(html));
		StringBuilder stringBuilder = valueInjector.nodeToStringBuilder(node);
		Assert.assertEquals(html, stringBuilder.toString());
	}
	
	@Test
	public void testProcessFlowDefinitionCurlyBrackets() throws Exception {
		String initialFlowDef = "{ docBase: '{{$baseNode}}', formLists: { main: [ { id: 'customer', url: '/forms/simplest/{{$firstForm}}', actions: [ 'finish' ] } ] } }";
		String expectedFlowDef = "{ docBase: '/myDocBase', formLists: { main: [ { id: 'customer', url: '/forms/simplest/simplest.html', actions: [ 'finish' ] } ] } }";
		StringBuilder flowStringBuilder = new StringBuilder(initialFlowDef);
		Properties flowProperties = new Properties();
		flowProperties.setProperty("baseNode", "/myDocBase");
		flowProperties.setProperty("firstForm", "simplest.html");
		
		Assert.assertEquals(initialFlowDef, flowStringBuilder.toString());
		
		valueInjector.processFlowDefinitionCurlyBrackets(flowStringBuilder, flowProperties);
		
		Assert.assertEquals(expectedFlowDef, flowStringBuilder.toString());
	}

}
