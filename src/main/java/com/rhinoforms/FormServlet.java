package com.rhinoforms;

import java.io.IOException;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.rhinoforms.flow.FieldSourceProxy;
import com.rhinoforms.flow.FieldSourceProxyException;
import com.rhinoforms.flow.FlowException;
import com.rhinoforms.flow.FlowExceptionBadRequest;
import com.rhinoforms.flow.FormFlow;
import com.rhinoforms.flow.FormFlowFactoryException;
import com.rhinoforms.flow.FormSubmissionHelperException;
import com.rhinoforms.flow.RemoteSubmissionHelperException;
import com.rhinoforms.flow.TransformHelperException;
import com.rhinoforms.formparser.FormParserException;
import com.rhinoforms.resourceloader.ResourceLoader;
import com.rhinoforms.resourceloader.ResourceLoaderException;
import com.rhinoforms.util.ServletHelper;
import com.rhinoforms.xml.DocumentHelper;
import com.rhinoforms.xml.DocumentHelperException;

@SuppressWarnings("serial")
public class FormServlet extends HttpServlet {

	private DocumentHelper documentHelper;
	private ResourceLoader resourceLoader;
	private ServletHelper servletHelper;
	private FlowRequestFactory flowRequestFactory;
	private FormActionRequestFactory formActionRequestFactory;
	private FormProducer formProducer;
	
	private static final Logger LOGGER = LoggerFactory.getLogger(FormServlet.class);
	private static final String PROXY_PATH_PREFIX = "/proxy/";
	private static final String VIEW_DATA_DOC_PATH_PREFIX = "/data-document/";
	private static final String FORM_RESOURCES_CHANGED_PREFIX = "/form-resources-changed";

	@Override
	public void init() throws ServletException {
		ServletContext servletContext = getServletContext();
		try {
			ApplicationContext appContext = new ApplicationContext(servletContext);
			this.servletHelper = appContext.getServletHelper();
			this.documentHelper = appContext.getDocumentHelper();
			this.resourceLoader = appContext.getResourceLoader();
			this.flowRequestFactory = appContext.getFlowRequestFactory();
			this.formActionRequestFactory = appContext.getFormActionRequestFactory();
			this.formProducer = appContext.getFormProducer();
		} catch (ResourceLoaderException e) {
			String message = "Failed to create ResourceLoader.";
			LOGGER.error(message, e);
			throw new ServletException(message);
		} catch (IOException e) {
			String message = "Failed to create master scope.";
			LOGGER.error(message, e);
			throw new ServletException(message);
		}
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
		FormActionRequest formActionRequest = formActionRequestFactory.createRequest(request);
		FormFlow formFlow = getFlowFromSession(request);
		
		if (formFlow != null) {
			
			try {
				formProducer.doActionWriteForm(formActionRequest, formFlow, response);
			} //handle better
			catch (RemoteSubmissionHelperException e){
				String message = e.getMessage();
				LOGGER.info(message, e);
			} catch (DocumentHelperException e) {
				String message = e.getMessage();
				LOGGER.info(message, e);
			} //end
			catch (FlowExceptionBadRequest e) {
				String message = e.getMessage();
				LOGGER.info(message, e);
				sendFrontendError(HttpServletResponse.SC_BAD_REQUEST, message, response);
			} catch (FlowException e) {
				String message = e.getMessage();
				LOGGER.info(message, e);
				String frontendMessage = e.getFrontendMessage();
				if (frontendMessage != null) {
					message = frontendMessage;
				}
				sendFrontendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, response);
			} catch (FormSubmissionHelperException e) {
				String message = e.getMessage();
				LOGGER.info(message, e);
				sendFrontendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, response);
			} catch (FormParserException e) {
				String message = e.getMessage();
				LOGGER.info(message, e);
				sendFrontendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, response);
			} catch (TransformerException e) {
				String message = "Failed to output DataDocument";
				LOGGER.error(message, e);
				sendFrontendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, response);
			} catch (TransformHelperException e) {
				String message = "Failed to transform DataDocument";
				LOGGER.error(message, e);
				sendFrontendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, response);
			}
		} else {
			sendFrontendError(HttpServletResponse.SC_FORBIDDEN, "Your session has expired.", response);
		}
	}

	private void doGetCreateFormFlow(HttpServletRequest request, HttpServletResponse response, HttpSession session) throws IOException,
			ServletException {
		FlowCreationRequest flowRequest = flowRequestFactory.createNewFlowRequest(request);
		try {
			formProducer.createFlowWriteForm(flowRequest, request.getSession(), response);
		} catch (FormFlowFactoryException e) {
			String message = "Failed to create form flow.";
			LOGGER.error(message, e);
			sendFrontendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, response);
		} catch (FormParserException e) {
			String message = "Failed to load the first form.";
			LOGGER.error(message, e);
			sendFrontendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, response);
		} catch (FlowException e) {
			String message = "Failed to navigate to the first form.";
			LOGGER.error(message, e);
			String frontendMessage = e.getFrontendMessage();
			if (frontendMessage != null) {
				message = frontendMessage;
			}
			sendFrontendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, response);
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
				sendFrontendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, response);
			}
		} else {
			String message = "Your session has expired.";
			LOGGER.debug(message);
			sendFrontendError(HttpServletResponse.SC_FORBIDDEN, message, response);
		}
	}

	private void doGetFormResourcesChangedNotification(HttpServletResponse response) throws IOException {
		try {
			resourceLoader.formResourcesChanged();
			response.getWriter().write("Successfully notified Resource Loader.");
		} catch (ResourceLoaderException e) {
			LOGGER.error(e.getMessage(), e);
			sendFrontendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), response);
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

	private void sendFrontendError(int errorCode, String message, HttpServletResponse response) throws IOException {
		response.setStatus(errorCode);
		response.setContentType("text/plain");
		response.getWriter().write(message);
	}

}
