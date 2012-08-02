package com.rhinoforms;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

import org.apache.log4j.Logger;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.SimpleHtmlSerializer;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.rhinoforms.serverside.InputPojo;

public class FormResponseWrapper extends HttpServletResponseWrapper {

	private static final Logger LOGGER = Logger.getLogger(FormResponseWrapper.class);
	private static final FieldPathHelper fieldPathHelper = new FieldPathHelper();
	
	private CharArrayWriter charArrayWriter;
	private PrintWriter printWriter;
	private PrintWriterOutputStream printWriterOutputStream;
	private int contentLength;

	public FormResponseWrapper(HttpServletResponse response) {
		super(response);
		charArrayWriter = new CharArrayWriter();
		printWriter = new PrintWriter(charArrayWriter);
		printWriterOutputStream = new PrintWriterOutputStream(printWriter);

		// Set no-cache headers - maybe should be in web.xml?
		response.setHeader("Expires", new Date().toString());
		response.setHeader("Last-Modified", new Date().toString());
		response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0, post-check=0, pre-check=0");
		response.setHeader("Pragma", "no-cache");
	}

	public void parseResponseAndWrite(ServletContext servletContext, HttpServletRequest request, HttpServletResponse response, FormFlow formFlow)
			throws IOException, XPatherException, TransformerConfigurationException, XPathExpressionException {

		printWriterOutputStream.flush();
		printWriterOutputStream.close();
		printWriter.flush();
		printWriter.close();

		PrintWriter writer = super.getWriter();
		char[] charArray = charArrayWriter.toCharArray();

		if (formFlow != null) {
			StringBuilder stringBuilder = new StringBuilder();
			stringBuilder.append(charArray);

			HtmlCleaner cleaner = new HtmlCleaner();
			TagNode documentNode = cleaner.clean(stringBuilder.toString());

			Document dataDocument = formFlow.getDataDocument();
			String documentBasePath = formFlow.getDocumentBasePath();
			
			Object[] rfFormNodes = documentNode.evaluateXPath("//form[@" + Constants.RHINOFORM_FLAG + "='true']");
			if (rfFormNodes.length > 0) {
				LOGGER.info(rfFormNodes.length + " forms found.");
				TagNode formNode = (TagNode) rfFormNodes[0];
	
				List<InputPojo> inputPojos = new ArrayList<InputPojo>();
				@SuppressWarnings("unchecked")
				List<TagNode> inputs = formNode.getElementListByName("input", false);
				for (TagNode inputTagNode : inputs) {
					String name = inputTagNode.getAttributeByName("name");
					String type = inputTagNode.getAttributeByName("type");
					String validation = inputTagNode.getAttributeByName("validation");
					String validationFunction = inputTagNode.getAttributeByName("validationFunction");
					XPathExpression xPathExpression = fieldPathHelper.fieldToXPathExpression(documentBasePath, name);
					NodeList nodeList = (NodeList) xPathExpression.evaluate(dataDocument, XPathConstants.NODESET);
					if (nodeList != null && nodeList.getLength() == 1) {
						String inputValue = nodeList.item(0).getTextContent();
						inputTagNode.setAttribute("value", inputValue);
					} else {
						LOGGER.warn("Multiple nodes matched for documentBasePath: '" + documentBasePath + "', field name: '" + name + "'. No value will be pushed into the form and there will be submission problems.");
					}
					
					inputPojos.add(new InputPojo(name, type, validation, validationFunction));
	
					LOGGER.debug("input " + name + " - validation:" + validation);
				}
	
				formFlow.setCurrentInputPojos(inputPojos);
	
				formNode.setAttribute("parsed", "true");
				TagNode flowIdNode = new TagNode("input");
				flowIdNode.setAttribute("name", Constants.FLOW_ID_FIELD_NAME);
				flowIdNode.setAttribute("type", "hidden");
				flowIdNode.setAttribute("value", formFlow.getId() + "");
				formNode.insertChild(0, flowIdNode);
	
				Scriptable scope = formFlow.getScope();
	
				String script = Constants.RHINOFORM_SCRIPT;
				Context jsContext = Context.enter();
				try {
					jsContext.evaluateReader(scope, new InputStreamReader(servletContext.getResourceAsStream(script)), script, 1, null);
	
					Object[] rfScriptNodes = documentNode.evaluateXPath("//script[@" + Constants.RHINOFORM_FLAG + "='true']");
					TagNode rfScriptNode = null;
					for (Object rfScriptNodeObject : rfScriptNodes) {
						rfScriptNode = (TagNode) rfScriptNodeObject;
						StringBuffer rfScriptNodeScript = rfScriptNode.getText();
						String rfScriptNodeScriptText = rfScriptNodeScript.toString();
						LOGGER.info("rfScriptNodeScript = " + rfScriptNodeScriptText);
						jsContext.evaluateString(scope, rfScriptNodeScriptText, "<cmd>", 1, null);
					}
					new SimpleHtmlSerializer(cleaner.getProperties()).write(documentNode, writer, "utf-8");
				} finally {
					Context.exit();
				}
			} else {
				LOGGER.warn("No forms found");
			}
		} else {
			writer.write(charArray);
		}
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		return printWriter;
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		return printWriterOutputStream;
	}

	@Override
	public void setContentLength(int len) {
		this.contentLength = len;
	}

	private static class PrintWriterOutputStream extends ServletOutputStream {

		private PrintWriter printWriter;

		public PrintWriterOutputStream(PrintWriter printWriter) {
			this.printWriter = printWriter;
		}

		@Override
		public void write(int b) throws IOException {
			printWriter.append((char) b);
		}

	}
	
}
