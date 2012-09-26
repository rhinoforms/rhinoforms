package com.rhinoforms;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.transform.TransformerException;

import org.mozilla.javascript.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rhinoforms.resourceloader.ResourceLoader;
import com.rhinoforms.resourceloader.ServletResourceLoader;
import com.rhinoforms.util.ServletHelper;

@SuppressWarnings("serial")
public class FormServlet extends HttpServlet {

	private FormFlowFactory formFlowFactory;
	private DocumentHelper documentHelper;
	private ResourceLoader resourceLoader;
	private JSMasterScope masterScope;
	private ServletHelper servletHelper;
	private FormSubmissionHelper formSubmissionHelper;
	private static final Logger LOGGER = LoggerFactory.getLogger(FormServlet.class);

	@Override
	public void init() throws ServletException {
		this.resourceLoader = new ServletResourceLoader(getServletContext());
		this.documentHelper = new DocumentHelper();
		this.servletHelper = new ServletHelper();

		Context jsContext = Context.enter();
		try {
			this.masterScope = new RhinoFormsMasterScopeFactory().createMasterScope(jsContext, resourceLoader);
		} catch (IOException e) {
			String message = "Failed to create master scope.";
			LOGGER.error(message, e);
			throw new ServletException(message);
		} finally {
			Context.exit();
		}

		this.formSubmissionHelper = new FormSubmissionHelper(masterScope);
		this.formFlowFactory = new FormFlowFactory(resourceLoader, masterScope);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String pathInfo = request.getPathInfo();
		HttpSession session = request.getSession();

		if (pathInfo != null) {
			LOGGER.debug("pathInfo = {}", pathInfo);
			String proxyPathPrefix = "/proxy/";
			if (pathInfo.startsWith(proxyPathPrefix)) {
				// Proxy request
				String proxyPath = pathInfo.substring(proxyPathPrefix.length());
				FormFlow formFlow = getFlow(request);
				if (formFlow != null) {

					FieldSourceProxy fieldSourceProxy = formFlow.getFieldSourceProxy(proxyPath);

					@SuppressWarnings("unchecked")
					Map<String, String[]> parameterMapMultiValue = request.getParameterMap();
					Map<String, String> parameterMap = servletHelper.mapOfArraysToMapOfFirstValues(parameterMapMultiValue);
					Set<String> paramsToRemove = new HashSet<String>();
					// remove rf.xx values
					for (String paramName : parameterMap.keySet()) {
						if (paramName.startsWith("rf.")) {
							paramsToRemove.add(paramName);
						}
					}
					for (String paramToRemove : paramsToRemove) {
						parameterMap.remove(paramToRemove);
					}
					try {
						fieldSourceProxy.makeRequest(parameterMap, response);
					} catch (FieldSourceProxyException e) {
						String message = "Failed to perform proxy request.";
						LOGGER.debug(message, e);
						sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, response);
					}
				} else {
					String message = "Your session has expired.";
					LOGGER.debug(message);
					sendError(HttpServletResponse.SC_FORBIDDEN, message, response);
				}
			}
		} else {
			// Create requested form flow
			String formFlowPath = request.getParameter(Constants.FLOW_PATH_PARAM);
			String initData = request.getParameter(Constants.INIT_DATA_PARAM);

			Context.enter();
			try {
				FormFlow newFormFlow = formFlowFactory.createFlow(formFlowPath, initData);
				SessionHelper.setFlow(newFormFlow, session);
				String formUrl = newFormFlow.navigateToFirstForm(documentHelper);
				forwardToAndParseForm(request, response, newFormFlow, formUrl);
			} catch (FormFlowFactoryException e) {
				String message = "Failed to create form flow.";
				LOGGER.error(message, e);
				sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, response);
			} catch (ActionError e) {
				String message = "Failed to navigate to the first form.";
				LOGGER.error(message, e);
				sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, response);
			} finally {
				Context.exit();
			}
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		HttpSession session = request.getSession();
		Context.enter();
		FormFlow formFlow = null;
		try {
			@SuppressWarnings("unchecked")
			Map<String, String[]> parameterMapMultiValue = request.getParameterMap();
			Map<String, String> parameterMap = servletHelper.mapOfArraysToMapOfFirstValues(parameterMapMultiValue);

			formFlow = getFlow(request);

			if (formFlow != null) {
				Map<String, String> actionParams = new HashMap<String, String>();
				String action = formSubmissionHelper.collectActionParameters(actionParams, parameterMap);

				Set<String> fieldsInError = null;
				if (!action.equals(FormFlow.CANCEL_ACTION)) {
					fieldsInError = formSubmissionHelper.validateAndPersist(formFlow, action, parameterMap);
				}

				if (fieldsInError == null || fieldsInError.isEmpty()) {
					// Find next form
					String nextUrl = null;
					if (action != null) {
						nextUrl = formFlow.doAction(action, actionParams, documentHelper);
					}

					if (nextUrl != null) {
						forwardToAndParseForm(request, response, formFlow, nextUrl);
						SessionHelper.setFlow(formFlow, session); // Required for Google AppEngine
					} else {
						// End of flow. Spit out XML.
						response.setContentType("text/plain");
						response.setHeader("rf.responseType", "data");
						PrintWriter writer = response.getWriter();
						documentHelper.documentToWriterPretty(formFlow.getDataDocument(), writer);
					}
				} else {
					sendError(HttpServletResponse.SC_BAD_REQUEST, "Validation error.", response);
				}
			} else {
				sendError(HttpServletResponse.SC_FORBIDDEN, "Your session has expired.", response);
			}
		} catch (ActionError e) {
			SessionHelper.removeFlow(formFlow, session);
			String message = "Failed to perform action, form session suspended.";
			LOGGER.error(message, e);
			sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, response);
		} catch (TransformerException e) {
			String message = "Failed to output the underlaying xml data.";
			LOGGER.error(message, e);
			sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, response);
		} finally {
			Context.exit();
		}
	}

	private FormFlow getFlow(HttpServletRequest request) throws ServletException {
		String parameter = request.getParameter(Constants.FLOW_ID_FIELD_NAME);
		if (parameter != null && parameter.matches("\\d+")) {
			int flowId = Integer.parseInt(parameter);
			HttpSession session = request.getSession();
			FormFlow formFlow = SessionHelper.getFlow(flowId, session);
			return formFlow;
		} else {
			throw new ServletException("Missing " + Constants.FLOW_ID_FIELD_NAME + ".");
		}
	}

	private void forwardToAndParseForm(HttpServletRequest request, HttpServletResponse response, FormFlow formFlow, String formUrl)
			throws ServletException, IOException {
		
		formUrl = formFlow.resolveResourcePathIfRelative(formUrl);

		RequestDispatcher requestDispatcher = getServletContext().getRequestDispatcher(formUrl);
		FormResponseWrapper formResponseWrapper = new FormResponseWrapper(response, resourceLoader);
		requestDispatcher.forward(request, formResponseWrapper);
		try {
			formResponseWrapper.parseResponseAndWrite(getServletContext(), formFlow, masterScope);
		} catch (Exception e) {
			String message = "Failed to load next form.";
			LOGGER.error(message, e);
			sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, response);
		}
	}
	
	private void sendError(int errorCode, String message, HttpServletResponse response) throws IOException {
		response.setStatus(errorCode);
		response.setContentType("text/plain");
		response.getWriter().write(message);
	}

}
