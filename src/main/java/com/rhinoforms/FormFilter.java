package com.rhinoforms;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;
import javax.xml.transform.TransformerConfigurationException;

import org.apache.log4j.Logger;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.SimpleHtmlSerializer;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import com.rhinoforms.serverside.InputPojo;

public class FormFilter implements Filter {

	private static final Logger LOGGER = Logger.getLogger(FormFilter.class);
	private ServletContext servletContext;
	private FormFlowFactory formFlowFactory;

	@Override
	public void init(FilterConfig config) throws ServletException {
		this.servletContext = config.getServletContext();
		formFlowFactory = new FormFlowFactory();
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		LOGGER.info("doFilter");
		if ("true".equals(servletRequest.getParameter(Constants.RHINOFORM_FLAG))) {
			HttpServletRequest request = (HttpServletRequest) servletRequest;
			HttpServletResponse response = (HttpServletResponse) servletResponse;

			response.setHeader("Expires", new Date().toString());
			response.setHeader("Last-Modified", new Date().toString());
			response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0, post-check=0, pre-check=0");
	        response.setHeader("Pragma", "no-cache");
			
			HttpSession session = request.getSession();
			FormFlow formFlow = formFlowFactory.createFlow();
			SessionHelper.addFlow(formFlow, session);
			
			HttpServletResponseWrapperA wrappedResponse = new HttpServletResponseWrapperA((HttpServletResponse) response);
			filterChain.doFilter(request, wrappedResponse);
			try {
				wrappedResponse.parseResponseAndWrite(servletContext, (HttpServletRequest) request, (HttpServletResponse) response, formFlow);
			} catch (Exception e) {
				LOGGER.error(e, e);
				throw new ServletException(e);
			}
		} else {
			filterChain.doFilter(servletRequest, servletResponse);
		}
	}

	@Override
	public void destroy() {
	}

	private static final class HttpServletResponseWrapperA extends HttpServletResponseWrapper {

		private CharArrayWriter charArrayWriter;
		private PrintWriter printWriter;
		private PrintWriterOutputStream printWriterOutputStream;
		private Context jsContext;
		private int contentLength;

		public HttpServletResponseWrapperA(HttpServletResponse response) {
			super(response);
			charArrayWriter = new CharArrayWriter();
			printWriter = new PrintWriter(charArrayWriter);
			printWriterOutputStream = new PrintWriterOutputStream(printWriter);
			this.jsContext = Context.enter();
		}

		private void parseResponseAndWrite(ServletContext servletContext, HttpServletRequest request, HttpServletResponse response,
				FormFlow formFlow) throws IOException, XPatherException, TransformerConfigurationException {

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

				Object[] rfFormNodes = documentNode.evaluateXPath("//form[@" + Constants.RHINOFORM_FLAG + "='true']");
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

}
