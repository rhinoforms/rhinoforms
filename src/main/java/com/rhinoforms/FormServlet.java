package com.rhinoforms;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.transform.TransformerException;

import org.mozilla.javascript.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.rhinoforms.flow.ActionError;
import com.rhinoforms.flow.FieldSourceProxy;
import com.rhinoforms.flow.FieldSourceProxyException;
import com.rhinoforms.flow.FlowAction;
import com.rhinoforms.flow.FlowActionType;
import com.rhinoforms.flow.FormFlow;
import com.rhinoforms.flow.FormFlowFactory;
import com.rhinoforms.flow.FormFlowFactoryException;
import com.rhinoforms.flow.RemoteSubmissionHelper;
import com.rhinoforms.formparser.FormParser;
import com.rhinoforms.js.JSMasterScope;
import com.rhinoforms.js.RhinoFormsMasterScopeFactory;
import com.rhinoforms.resourceloader.ResourceLoader;
import com.rhinoforms.resourceloader.ResourceLoaderException;
import com.rhinoforms.util.ServletHelper;
import com.rhinoforms.util.StringUtils;
import com.rhinoforms.xml.DocumentHelper;

@SuppressWarnings("serial")
public class FormServlet extends HttpServlet {

	private FormFlowFactory formFlowFactory;
	private DocumentHelper documentHelper;
	private ResourceLoader resourceLoader;
	private JSMasterScope masterScope;
	private ServletHelper servletHelper;
	private FormSubmissionHelper formSubmissionHelper;
	private FormParser formParser;
	private RemoteSubmissionHelper remoteSubmissionHelper;
	private ApplicationContext applicationContext;
	private static final Logger LOGGER = LoggerFactory.getLogger(FormServlet.class);
	private static final String PROXY_PATH_PREFIX = "/proxy/";
	private static final String VIEW_DATA_DOC_PATH_PREFIX = "/data-document/";
	private static final String FORM_RESOURCES_CHANGED_PREFIX = "/form-resources-changed";

	@Override
	public void init() throws ServletException {
		ServletContext servletContext = getServletContext();
		this.documentHelper = new DocumentHelper();
		this.servletHelper = new ServletHelper();

		Context jsContext = Context.enter();
		try {
			this.applicationContext = new ApplicationContext(servletContext);
			this.resourceLoader = applicationContext.getResourceLoader();
			this.formParser = new FormParser(resourceLoader);
			this.masterScope = new RhinoFormsMasterScopeFactory().createMasterScope(jsContext, resourceLoader);
		} catch (ResourceLoaderException e) {
			String message = "Failed to create ResourceLoader.";
			LOGGER.error(message, e);
			throw new ServletException(message);
		} catch (IOException e) {
			String message = "Failed to create master scope.";
			LOGGER.error(message, e);
			throw new ServletException(message);
		} finally {
			Context.exit();
		}

		this.formSubmissionHelper = new FormSubmissionHelper(masterScope);
		this.formFlowFactory = new FormFlowFactory(resourceLoader, masterScope, servletContext.getContextPath());

		this.remoteSubmissionHelper = new RemoteSubmissionHelper(resourceLoader);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String pathInfo = request.getPathInfo();
		HttpSession session = request.getSession();

		// Method switch based on request URL
		if (pathInfo == null) {
			// New flow request
			doGetCreateFormFlow(request, response, session);
		} else {
			LOGGER.debug("pathInfo = {}", pathInfo);
			if (pathInfo.startsWith(VIEW_DATA_DOC_PATH_PREFIX)) {
				// View DataDocument request
				String remainingPathInfo = pathInfo.substring(VIEW_DATA_DOC_PATH_PREFIX.length());
				doGetShowDataDocument(response, session, remainingPathInfo);
			} else if (pathInfo.startsWith(PROXY_PATH_PREFIX)) {
				// Proxy request
				String remainingPathInfo = pathInfo.substring(PROXY_PATH_PREFIX.length());
				doGetProxyRequest(request, response, remainingPathInfo);
			} else if (pathInfo.startsWith(FORM_RESOURCES_CHANGED_PREFIX)) {
				// Form resources changed notification request
				doGetFormResourcesChangedNotification(response);
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

			formFlow = getFlowFromSession(request);

			if (formFlow != null) {
				Map<String, String> actionParams = new HashMap<String, String>();
				String action = formSubmissionHelper.collectActionParameters(actionParams, parameterMap);

				FlowAction flowAction = formFlow.getCurrentActions().get(action);
				Set<String> fieldsInError = null;
				if (flowAction != null) {
					FlowActionType actionType = flowAction.getType();
					if (actionType != FlowActionType.CANCEL) {
						fieldsInError = formSubmissionHelper.validateAndPersist(formFlow, actionType, parameterMap);
					}
				}

				if (fieldsInError == null || fieldsInError.isEmpty()) {
					// Find next form
					String nextUrl = null;
					if (action != null) {
						formFlow.setRemoteSubmissionHelper(remoteSubmissionHelper);
						nextUrl = formFlow.doAction(action, actionParams, documentHelper);
					}

					if (nextUrl != null) {
						boolean suppressDebugBar = StringUtils.isStringTrueNullSafe(parameterMap.get(Constants.SUPPRESS_DEBUG_BAR_PARAM));
						forwardToAndParseForm(request, response, formFlow, nextUrl, suppressDebugBar);
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

	private void doGetCreateFormFlow(HttpServletRequest request, HttpServletResponse response, HttpSession session) throws IOException,
			ServletException {
		String formFlowPath = request.getParameter(Constants.FLOW_PATH_PARAM);
		String initData = request.getParameter(Constants.INIT_DATA_PARAM);

		Context.enter();
		try {
			FormFlow newFormFlow = formFlowFactory.createFlow(formFlowPath, initData);
			SessionHelper.setFlow(newFormFlow, session);
			String formUrl = newFormFlow.navigateToFirstForm(documentHelper);
			boolean suppressDebugBar = StringUtils.isStringTrueNullSafe(request.getParameter(Constants.SUPPRESS_DEBUG_BAR_PARAM));
			forwardToAndParseForm(request, response, newFormFlow, formUrl, suppressDebugBar);
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

	private void forwardToAndParseForm(HttpServletRequest request, HttpServletResponse response, FormFlow formFlow, String formUrl,
			boolean suppressDebugBar) throws ServletException, IOException {

		formUrl = formFlow.resolveResourcePathIfRelative(formUrl);
		String currentFormId = formFlow.getCurrentFormId();
		response.setHeader("rf.formId", currentFormId);

		try {
			InputStream formStream = resourceLoader.getFormResourceAsStream(formUrl);
			response.setContentType("text/html");
			formParser.parseForm(formStream, formFlow, response.getWriter(), masterScope, suppressDebugBar);
		} catch (Exception e) {
			String message = "Failed to load next form.";
			LOGGER.error(message, e);
			sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, response);
		}
	}

	private void doGetShowDataDocument(HttpServletResponse response, HttpSession session, String flowId) throws IOException,
			ServletException {
		FormFlow formFlow = SessionHelper.getFlow(flowId, session);
		if (formFlow != null) {
			Document dataDocument = formFlow.getDataDocument();
			try {
				response.setContentType("text/xml");
				documentHelper.documentToWriterPretty(dataDocument, response.getWriter(), false);
			} catch (TransformerException e) {
				throw new ServletException("Failed to display dataDocument", e);
			}
		} else {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No flow found with ID '" + flowId + "'");
		}
	}

	private void doGetProxyRequest(HttpServletRequest request, HttpServletResponse response, String proxyPath) throws ServletException,
			IOException {
		FormFlow formFlow = getFlowFromSession(request);
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

	private void doGetFormResourcesChangedNotification(HttpServletResponse response) throws IOException {
		try {
			resourceLoader.formResourcesChanged();
			response.getWriter().write("Successfully notified Resource Loader.");
		} catch (ResourceLoaderException e) {
			LOGGER.error(e.getMessage(), e);
			sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), response);
		}
	}

	private FormFlow getFlowFromSession(HttpServletRequest request) throws ServletException {
		String parameter = request.getParameter(Constants.FLOW_ID_FIELD_NAME);
		if (parameter != null && parameter.matches("\\d+")) {
			String flowId = parameter;
			HttpSession session = request.getSession();
			FormFlow formFlow = SessionHelper.getFlow(flowId, session);
			return formFlow;
		} else {
			throw new ServletException("Missing " + Constants.FLOW_ID_FIELD_NAME + ".");
		}
	}

	private void sendError(int errorCode, String message, HttpServletResponse response) throws IOException {
		response.setStatus(errorCode);
		response.setContentType("text/plain");
		response.getWriter().write(message);
	}

}
