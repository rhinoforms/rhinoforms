package com.rhinoforms;

import static com.rhinoforms.TestUtil.createDocument;
import static com.rhinoforms.TestUtil.serialiseHtmlCleanerNode;

import java.io.FileInputStream;

import junit.framework.Assert;

import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

public class ValueInjectorTest {

	private ValueInjector valueInjector;
	private HtmlCleaner htmlCleaner;
	private TagNode formHtml;
	private Document dataDocument;

	@Before
	public void setup() throws Exception {
		htmlCleaner = new HtmlCleaner();
		formHtml = htmlCleaner.clean(new FileInputStream("src/test/resources/fishes.html"));
		dataDocument = createDocument("<myData><ocean><name>Pacific</name><fishes><fish><name>One</name></fish><fish><name>Two</name></fish></fishes></ocean></myData>");

		this.valueInjector = new ValueInjector();
	}

	@Test
	public void testRecuringEntityOutput() throws Exception {

		valueInjector.processForEachStatements(formHtml, dataDocument, "/myData/ocean");

		String actual = serialiseHtmlCleanerNode(formHtml);
		Assert.assertFalse(actual.contains("foreach"));
		Assert.assertTrue(actual.contains("<span index=\"1\">One</span>"));
		Assert.assertTrue(actual.contains("<span index=\"2\">Two</span>"));
	}

	@Test
	public void testProcessRemainingCurlyBrackets() throws Exception {
		Assert.assertTrue("Placeholder present.", serialiseHtmlCleanerNode(formHtml).contains("<span class=\"ocean\">{{name}}</span>"));

		valueInjector.processRemainingCurlyBrackets(formHtml, dataDocument, "/myData/ocean");

		String actual = serialiseHtmlCleanerNode(formHtml);
		Assert.assertTrue("Placeholder replaced with dataDocument content.", actual.contains("<span class=\"ocean\">Pacific</span>"));
		Assert.assertTrue(actual.contains("Fish count: 2"));
		Assert.assertTrue(actual.contains("First fish: One"));
		System.out.println(actual);
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

}
