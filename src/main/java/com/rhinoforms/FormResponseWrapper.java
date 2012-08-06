package com.rhinoforms;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.htmlcleaner.XPatherException;

public class FormResponseWrapper extends HttpServletResponseWrapper {

	private CharArrayWriter charArrayWriter;
	private PrintWriter printWriter;
	private PrintWriterOutputStream printWriterOutputStream;
	private int contentLength;
	private FormParser formParser;

	public FormResponseWrapper(HttpServletResponse response, ResourceLoader resourceLoader) {
		super(response);
		charArrayWriter = new CharArrayWriter();
		printWriter = new PrintWriter(charArrayWriter);
		printWriterOutputStream = new PrintWriterOutputStream(printWriter);
		this.formParser = new FormParser(resourceLoader);

		// Set no-cache headers - maybe should be in web.xml?
		response.setHeader("Expires", new Date().toString());
		response.setHeader("Last-Modified", new Date().toString());
		response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0, post-check=0, pre-check=0");
		response.setHeader("Pragma", "no-cache");
	}

	public void parseResponseAndWrite(ServletContext servletContext, FormFlow formFlow)
			throws IOException, XPatherException, TransformerConfigurationException, XPathExpressionException {

		printWriterOutputStream.flush();
		printWriterOutputStream.close();
		printWriter.flush();
		printWriter.close();

		PrintWriter writer = super.getWriter();
		char[] charArray = charArrayWriter.toCharArray();

		if (formFlow != null) {
			formParser.parseForm(new String(charArray), formFlow, writer);
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
