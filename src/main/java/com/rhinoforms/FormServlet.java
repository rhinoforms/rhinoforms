package com.rhinoforms;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.log4j.Logger;
import org.htmlcleaner.XPatherException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.w3c.dom.Document;

import com.rhinoforms.serverside.InputPojo;

@SuppressWarnings("serial")
public class FormServlet extends HttpServlet {

	private static final Logger LOGGER = Logger.getLogger(FormServlet.class);
	private FormFlowFactory formFlowFactory;
	private DocumentManipulator documentManipulator;

	@Override
	public void init() throws ServletException {
		this.formFlowFactory = new FormFlowFactory();
		this.documentManipulator = new DocumentManipulator();
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
		} catch (FormFlowFactoryException e) {
			throw new ServletException(e.getMessage(), e);
		} finally {
			Context.exit();
		}
	}

	private void loadForm(HttpServletRequest request, HttpServletResponse response, FormFlow formFlow, String formUrl)
			throws ServletException, IOException {
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
			}

			String jsPojoMap = inputPOJOListtoJS(inputPOJOs);
			LOGGER.debug("inputPojos as js:" + jsPojoMap);

			StringBuilder commandStringBuilder = new StringBuilder();
			commandStringBuilder.append("rf.validateFields(");
			commandStringBuilder.append(jsPojoMap);
			commandStringBuilder.append(")");

			Set<String> fieldsInError = new HashSet<String>();
			Context jsContext = Context.enter();
			try {
				NativeArray errors = (NativeArray) jsContext.evaluateString(scope, commandStringBuilder.toString(), "<cmd>", 1, null);
				for (int i = 0; i < errors.getLength(); i++) {
					ScriptableObject error = (ScriptableObject) errors.get(i, scope);
					fieldsInError.add(error.get("name", scope).toString());
				}
			} finally {
				Context.exit();
			}

			if (!fieldsInError.isEmpty() && action.equals(FormFlow.BACK_ACTION)) {
				HashSet<InputPojo> inputPOJOsToRemove = new HashSet<InputPojo>();
				for (InputPojo inputPojo : inputPOJOs) {
					if (fieldsInError.contains(inputPojo.name)) {
						inputPOJOsToRemove.add(inputPojo);
					}
				}
				inputPOJOs.removeAll(inputPOJOsToRemove);
				fieldsInError.clear();
			}

			if (fieldsInError.isEmpty()) {

				// Persist collected form data
				String documentBasePath = formFlow.getDocumentBasePath();
				Document dataDocument = formFlow.getDataDocument();
				try {
					documentManipulator.persistFormData(inputPOJOs, documentBasePath, dataDocument);
				} catch (DocumentManipulatorException e) {
					String message = "Failed to map field to xml document.";
					LOGGER.error(message, e);
					throw new ServletException(message, e);
				}

				// Find next form
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

					response.setContentType("text/plain");
					response.setHeader("rf.responseType", "data");
					PrintWriter writer = response.getWriter();
					try {
						documentManipulator.documentToWriter(dataDocument, writer);
					} catch (TransformerException e) {
						String message = "Failed to output the underlaying xml data.";
						LOGGER.error(message, e);
						response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message);
					}
				}
			} else {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Validation error.");
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
