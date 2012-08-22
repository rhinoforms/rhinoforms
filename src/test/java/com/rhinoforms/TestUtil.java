package com.rhinoforms;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.SimpleHtmlSerializer;
import org.htmlcleaner.TagNode;
import org.w3c.dom.Document;

/**
 * Hacky methods to help build tests
 */
public class TestUtil {

	public static String readFileContents(String filePath) throws IOException {
		StringBuilder builder = new StringBuilder();
		BufferedReader reader = new BufferedReader(new FileReader(filePath));
		while (reader.ready()) {
			builder.append(reader.readLine());
			builder.append("\n");
		}
		return builder.toString();
	}
	
	public static Document createDocument(String dataDocumentString) throws Exception {
		DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		return documentBuilder.parse(new ByteArrayInputStream(dataDocumentString.getBytes()));
	}
	
	public static String serialiseNode(TagNode node) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		CleanerProperties properties = new HtmlCleaner().getProperties();
		properties.setOmitXmlDeclaration(true);
		new SimpleHtmlSerializer(properties).write(node, new OutputStreamWriter(outputStream), "utf-8");
		String actual = new String(outputStream.toByteArray());
		return actual;
	}
	
}
