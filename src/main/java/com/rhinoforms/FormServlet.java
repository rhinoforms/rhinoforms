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

	final Logger logger = LoggerFactory.getLogger(FormServlet.class);
	private FormFlowFactory formFlowFactory;
	private DocumentHelper documentHelper;
	private ResourceLoader resourceLoader;
	private JSMasterScope masterScope;
	private ServletHelper servletHelper;
	private FormSubmissionHelper formSubmissionHelper;

	@Override
	public void init() throws ServletException {
		this.formFlowFactory = new FormFlowFactory();
		this.documentHelper = new DocumentHelper();
		this.resourceLoader = new ServletResourceLoader(getServletContext());
		this.servletHelper = new ServletHelper();

		Context jsContext = Context.enter();
		try {
			this.masterScope = new RhinoFormsMasterScopeFactory().createMasterScope(jsContext, resourceLoader);
		} catch (IOException e) {
			String message = "Failed to create master scope.";
			logger.error(message, e);
			throw new ServletException(message);
		} finally {
			Context.exit();
		}

		this.formSubmissionHelper = new FormSubmissionHelper(masterScope);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String pathInfo = request.getPathInfo();
		HttpSession session = request.getSession();

		if (pathInfo != null) {
			logger.debug("pathInfo = {}", pathInfo);
			String proxyPathPrefix = "/proxy/";
			if (pathInfo.startsWith(proxyPathPrefix)) {
				// Proxy request
				String proxyPath = pathInfo.substring(proxyPathPrefix.length());
				FormFlow formFlow = getFlow(request);
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
					response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to perform proxy request.");
				}
			}
		} else {
			// Create requested form flow
			String formFlowPath = request.getParameter(Constants.FLOW_PATH_PARAM);
			String initData = request.getParameter(Constants.INIT_DATA_PARAM);

			Context jsContext = Context.enter();
			try {
				String realFormFlowPath = getServletContext().getRealPath(formFlowPath);
				FormFlow newFormFlow = formFlowFactory.createFlow(realFormFlowPath, jsContext, initData);
				SessionHelper.addFlow(newFormFlow, session);
				String formUrl = newFormFlow.navigateToFirstForm(documentHelper);
				forwardToAndParseForm(request, response, newFormFlow, formUrl);
			} catch (FormFlowFactoryException e) {
				throw new ServletException(e.getMessage(), e);
			} catch (ActionError e) {
				throw new ServletException(e.getMessage(), e);
			} finally {
				Context.exit();
			}
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		Context.enter();
		try {
			@SuppressWarnings("unchecked")
			Map<String, String[]> parameterMapMultiValue = request.getParameterMap();
			Map<String, String> parameterMap = servletHelper.mapOfArraysToMapOfFirstValues(parameterMapMultiValue);

			FormFlow formFlow = getFlow(request);

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
						try {
							nextUrl = formFlow.doAction(action, actionParams, documentHelper);
						} catch (ActionError e) {
							logger.error(e.getMessage(), e);
							throw new ServletException(e);
						}
					}

					if (nextUrl != null) {
						forwardToAndParseForm(request, response, formFlow, nextUrl);
					} else {
						// End of flow. Spit out XML.
						response.setContentType("text/plain");
						response.setHeader("rf.responseType", "data");
						PrintWriter writer = response.getWriter();
						try {
							documentHelper.documentToWriterPretty(formFlow.getDataDocument(), writer);
						} catch (TransformerException e) {
							String message = "Failed to output the underlaying xml data.";
							logger.error(message, e);
							response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message);
						}
					}
				} else {
					response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Validation error.");
				}
			} else {
				response.sendError(HttpServletResponse.SC_FORBIDDEN, "Your session has expired.");
			}
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
			throw new ServletException("Missing " + Constants.FLOW_ID_FIELD_NAME);
		}
	}

	private void forwardToAndParseForm(HttpServletRequest request, HttpServletResponse response, FormFlow formFlow, String formUrl)
			throws ServletException, IOException {
		RequestDispatcher requestDispatcher = getServletContext().getRequestDispatcher(formUrl);
		FormResponseWrapper formResponseWrapper = new FormResponseWrapper(response, resourceLoader);
		requestDispatcher.forward(request, formResponseWrapper);
		try {
			formResponseWrapper.parseResponseAndWrite(getServletContext(), formFlow, masterScope);
		} catch (Exception e) {
			String message = "Failed to load next form.";
			logger.error(message, e);
			throw new ServletException(message);
		}
	}

}
