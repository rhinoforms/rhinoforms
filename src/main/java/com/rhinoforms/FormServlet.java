package com.rhinoforms;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.log4j.Logger;
import org.htmlcleaner.XPatherException;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.rhinoforms.serverside.InputPojo;

@SuppressWarnings("serial")
public class FormServlet extends HttpServlet {

	private static final Logger LOGGER = Logger.getLogger(FormServlet.class);
	private FormFlowFactory formFlowFactory;

	@Override
	public void init() throws ServletException {
		this.formFlowFactory = new FormFlowFactory();
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String formFlowPath = request.getParameter(Constants.FLOW_PATH_PARAM);
		String initData = request.getParameter(Constants.INIT_DATA_PARAM);
		
		Context jsContext = Context.enter();
		try {
			String realFormFlowPath = getServletContext().getRealPath(formFlowPath);
			FormFlow newFormFlow = formFlowFactory.createFlow(realFormFlowPath, jsContext, initData);
			SessionHelper.addFlow(newFormFlow, request.getSession());
			String formUrl = newFormFlow.navigateToFirstForm();
			loadForm(request, response, newFormFlow, formUrl);
		} finally {
			Context.exit();
		}
	}

	private void loadForm(HttpServletRequest request, HttpServletResponse response, FormFlow formFlow, String formUrl) throws ServletException, IOException {
		RequestDispatcher requestDispatcher = getServletContext().getRequestDispatcher(formUrl);
		FormResponseWrapper formResponseWrapper = new FormResponseWrapper(response);
		requestDispatcher.forward(request, formResponseWrapper);
		try {
			formResponseWrapper.parseResponseAndWrite(getServletContext(), request, response, formFlow);
		} catch (TransformerConfigurationException e) {
			LOGGER.error(e, e);// TODO: error handling
		} catch (XPatherException e) {
			LOGGER.error(e, e);// TODO: error handling
		} catch (XPathExpressionException e) {
			LOGGER.error(e, e);// TODO: error handling
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		HttpSession session = request.getSession();

		int flowId = Integer.parseInt(request.getParameter(Constants.FLOW_ID_FIELD_NAME));
		String action = request.getParameter("rf.action");

		FormFlow formFlow = SessionHelper.getFlow(flowId, session);
		Scriptable scope = formFlow.getScope();
		Map<String, String> data = formFlow.getData();

		if (scope != null) {
			List<InputPojo> inputPOJOs = formFlow.getCurrentInputPojos();
			for (InputPojo inputPOJO : inputPOJOs) {
				if (inputPOJO.type.equalsIgnoreCase("checkbox")) {
					inputPOJO.value = request.getParameter(inputPOJO.name) != null ? "true" : "false";
				} else {
					inputPOJO.value = request.getParameter(inputPOJO.name);
				}
				data.put(inputPOJO.name, inputPOJO.value);
			}

			String jsPojoMap = inputPOJOListtoJS(inputPOJOs);
			LOGGER.debug("inputPojos as js:" + jsPojoMap);

			StringBuilder commandStringBuilder = new StringBuilder();
			commandStringBuilder.append("rf.validateFields(");
			commandStringBuilder.append(jsPojoMap);
			commandStringBuilder.append(")");

			Context jsContext = Context.enter();
			try {
				NativeArray errors = (NativeArray) jsContext.evaluateString(scope, commandStringBuilder.toString(), "<cmd>", 1, null);
				for (int i = 0; i < errors.getLength(); i++) {
					Object error = errors.get(i, scope);
					LOGGER.info("Error - " + error);
				}
			} finally {
				Context.exit();
			}

			String nextUrl = null;
			if (action != null) {
				try {
					nextUrl = formFlow.navigateFlow(action);
				} catch (NavigationError e) {
					throw new ServletException(e);
				}
			}

			if (nextUrl != null) {
				loadForm(request, response, formFlow, nextUrl);
			} else {
				// End of flow
				// Build XML from submitted values
				StringBuilder xmlStringBuilder = new StringBuilder();
				xmlStringBuilder.append("<data>\n");
				for (String fieldName : data.keySet()) {
					String value = data.get(fieldName);
					if (value != null) {
						xmlStringBuilder.append("<").append(fieldName).append(">");
						xmlStringBuilder.append(value);
						xmlStringBuilder.append("</").append(fieldName).append(">\n");
					}
				}
				xmlStringBuilder.append("</data>\n");

				response.setContentType("text/plain");
				response.setHeader("rf.responseType", "data");
				PrintWriter writer = response.getWriter();
				writer.write(xmlStringBuilder.toString());
			}
		} else {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "Your session has expired.");
		}
	}

	private String inputPOJOListtoJS(List<InputPojo> inputPojos) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("{");
		boolean first = true;
		for (InputPojo inputPOJO : inputPojos) {
			// { name:name, value:value, validation:validation,
			// validationFunction:validationFunction };
			if (first) {
				first = false;
			} else {
				stringBuilder.append(",");
			}
			stringBuilder.append("\"");
			stringBuilder.append(inputPOJO.name);
			stringBuilder.append("\":");
			stringBuilder.append("{");
			stringBuilder.append("name:\"");
			stringBuilder.append(inputPOJO.name);
			stringBuilder.append("\",");
			stringBuilder.append("value:");
			if (inputPOJO.type.equalsIgnoreCase("checkbox")) {
				stringBuilder.append(inputPOJO.value);
			} else {
				stringBuilder.append("\"");
				stringBuilder.append(inputPOJO.value);
				stringBuilder.append("\"");
			}
			if (inputPOJO.validation != null) {
				stringBuilder.append(",validation:\"");
				stringBuilder.append(inputPOJO.validation);
				stringBuilder.append("\"");
			}
			if (inputPOJO.validationFunction != null) {
				stringBuilder.append(",validationFunction:\"");
				stringBuilder.append(inputPOJO.validationFunction.replaceAll("\"", "'"));
				stringBuilder.append("\"");
			}
			stringBuilder.append("}");
		}
		stringBuilder.append("}");
		return stringBuilder.toString();
	}

}
