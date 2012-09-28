package com.rhinoforms;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class FormResponseWrapper extends HttpServletResponseWrapper {

	private CharArrayWriter charArrayWriter;
	private PrintWriter printWriter;
	private PrintWriterOutputStream printWriterOutputStream;
	private FormParser formParser;

	public FormResponseWrapper(HttpServletResponse response, FormParser formParser) {
		super(response);
		this.formParser = formParser;
		
		charArrayWriter = new CharArrayWriter();
		printWriter = new PrintWriter(charArrayWriter);
		printWriterOutputStream = new PrintWriterOutputStream(printWriter);

		// Set no-cache headers - maybe should be in web.xml?
		response.setHeader("Expires", new Date().toString());
		response.setHeader("Last-Modified", new Date().toString());
		response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0, post-check=0, pre-check=0");
		response.setHeader("Pragma", "no-cache");
	}

	public void parseResponseAndWrite(ServletContext servletContext, FormFlow formFlow, JSMasterScope masterScope) throws FormResponseWrapperException {
		try {
			printWriterOutputStream.flush();
			printWriterOutputStream.close();
			printWriter.flush();
			printWriter.close();
	
			PrintWriter writer = super.getWriter();
			char[] charArray = charArrayWriter.toCharArray();
	
			String formContentsString = new String(charArray);
			formParser.parseForm(formContentsString, formFlow, writer, masterScope);
		} catch (Exception e) {
			throw new FormResponseWrapperException(e);
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
		// Discard contentLength, it will change when the form is parsed.
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
