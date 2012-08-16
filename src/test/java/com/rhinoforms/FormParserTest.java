package com.rhinoforms;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;

import com.rhinoforms.serverside.InputPojo;

public class FormParserTest {

	private FormParser formParser;
	private FormFlow formFlow;
	private JSMasterScope masterScope;

	@Before
	public void setup() throws Exception {
		TestResourceLoader resourceLoader = new TestResourceLoader();
		this.formParser = new FormParser(resourceLoader);
		this.formFlow = new FormFlowFactory().createFlow("src/test/resources/test-flow1.js", Context.enter(), "<myData><fishes><fish><name>One</name></fish><fish><name>Two</name></fish></fishes></myData>");
		this.formFlow.navigateToFirstForm();
		
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
		System.out.println(actual);
		Assert.assertTrue(actual.contains("<span index=\"1\">One</span>"));
		Assert.assertTrue(actual.contains("<span index=\"2\">Two</span>"));
	}

	@Test
	public void testAllInputTypes() throws Exception {
		this.formFlow = new FormFlowFactory().createFlow("src/test/resources/test-flow1.js", Context.enter(), "<myData><terms>disagree</terms><title>Miss</title></myData>");
		this.formFlow.navigateToFirstForm();
		
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		formParser.parseForm(readFileContents("src/test/resources/all-input-types.html"), formFlow, new PrintWriter(byteArrayOutputStream), masterScope);
		
		List<InputPojo> inputPojos = formFlow.getCurrentInputPojos();
		
		Assert.assertEquals(3, inputPojos.size());
		Assert.assertEquals("terms", inputPojos.get(0).getName());
		Assert.assertEquals("firstName", inputPojos.get(1).getName());
		Assert.assertEquals("title", inputPojos.get(2).getName());
		String parsedFormHtml = new String(byteArrayOutputStream.toByteArray());
		Assert.assertTrue(parsedFormHtml.contains("name=\"terms\" value=\"disagree\" checked=\"true\""));
		Assert.assertTrue(parsedFormHtml.contains("<option selected=\"selected\">Miss</option>"));
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

}
