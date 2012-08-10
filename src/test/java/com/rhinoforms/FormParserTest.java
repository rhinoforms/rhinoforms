package com.rhinoforms;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;

import com.rhinoforms.resourceloader.ResourceLoader;

public class FormParserTest {

	private FormParser formParser;
	private FormFlow formFlow;
	private static ResourceLoader resourceLoader = getResourceLoader();

	@Before
	public void setup() throws Exception {
		this.formParser = new FormParser(resourceLoader);
		this.formFlow = new FormFlowFactory().createFlow("src/test/resources/test-flow1.js", Context.enter(), "<myData><fishes><fish><name>One</name></fish><fish><name>Two</name></fish></fishes></myData>");
		this.formFlow.navigateToFirstForm();
	}

	@Test
	public void testRecuringEntityOutput() throws Exception {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		formParser.parseForm(readFileContents("src/test/resources/fishes.html"), formFlow, new PrintWriter(outputStream));
		String actual = new String(outputStream.toByteArray());
		System.out.println(actual);
		Assert.assertTrue(actual.contains("<span index=\"1\">One</span>"));
		Assert.assertTrue(actual.contains("<span index=\"2\">Two</span>"));
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

	private static ResourceLoader getResourceLoader() {
		return new ResourceLoader() {
			@Override
			public InputStream getResourceAsStream(String path) throws FileNotFoundException {
				return new FileInputStream("src/main/webapp/" + path);
			}
		};
	}

}
