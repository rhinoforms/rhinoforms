package com.rhinoforms;

import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.SimpleHtmlSerializer;
import org.htmlcleaner.TagNode;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;

/**
 * Hacky methods to help build tests
 */
public class TestUtil {

	public static String readFileContents(String filePath) throws IOException {
		StringBuilder builder = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			while (reader.ready()) {
				builder.append(reader.readLine());
				builder.append(Constants.NEW_LINE);
			}
		}
		return builder.toString();
	}
	
	public static Document createDocument(String dataDocumentString) throws Exception {
		DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		return documentBuilder.parse(new ByteArrayInputStream(dataDocumentString.getBytes()));
	}
	
	public static String serialiseHtmlCleanerNode(TagNode node) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		CleanerProperties properties = new HtmlCleaner().getProperties();
		properties.setOmitXmlDeclaration(true);
		new SimpleHtmlSerializer(properties).write(node, new OutputStreamWriter(outputStream), "utf-8");
		String actual = new String(outputStream.toByteArray());
		return actual;
	}
	
}
