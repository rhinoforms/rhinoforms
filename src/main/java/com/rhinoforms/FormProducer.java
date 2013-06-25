package com.rhinoforms;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.transform.TransformerException;

import org.mozilla.javascript.Context;

import com.rhinoforms.flow.FlowExceptionActionError;
import com.rhinoforms.flow.FlowExceptionBadRequest;
import com.rhinoforms.flow.FlowExceptionJavaScript;
import com.rhinoforms.flow.FormFlow;
import com.rhinoforms.flow.FormFlowFactory;
import com.rhinoforms.flow.FormFlowFactoryException;
import com.rhinoforms.flow.FormSubmissionHelper;
import com.rhinoforms.flow.FormSubmissionHelperException;
import com.rhinoforms.flow.FormSubmissionResult;
import com.rhinoforms.flow.RemoteSubmissionHelper;
import com.rhinoforms.flow.SubmissionTimeKeeper;
import com.rhinoforms.formparser.FormParser;
import com.rhinoforms.formparser.FormParserException;
import com.rhinoforms.js.FlowExceptionFileNotFound;
import com.rhinoforms.js.JSMasterScope;
import com.rhinoforms.resourceloader.ResourceLoader;
import com.rhinoforms.xml.DocumentHelper;
import com.rhinoforms.xml.FlowExceptionXPath;

public class FormProducer {

	private FormFlowFactory formFlowFactory;
	private DocumentHelper documentHelper;
	private JSMasterScope masterScope;
	private ResourceLoader resourceLoader;
	private FormParser formParser;
	private FormSubmissionHelper formSubmissionHelper;
	private RemoteSubmissionHelper remoteSubmissionHelper;
	private SubmissionTimeKeeper submissionTimeKeeper;

	public FormProducer(ApplicationContext appContext) {
		formFlowFactory = appContext.getFormFlowFactory();
		documentHelper = appContext.getDocumentHelper();
		masterScope = appContext.getMasterScope();
		resourceLoader = appContext.getResourceLoader();
		formParser = appContext.getFormParser();
		formSubmissionHelper = appContext.getFormSubmissionHelper();
		remoteSubmissionHelper = appContext.getRemoteSubmissionHelper();
		submissionTimeKeeper = appContext.getSubmissionTimeKeeper();
	}

	public void createFlowWriteForm(FlowCreationRequest flowRequest, HttpSession session, HttpServletResponse response) throws IOException, FormFlowFactoryException, FlowExceptionActionError, FlowExceptionXPath, FormParserException {
		Context.enter();
		try {
			FormFlow newFormFlow = formFlowFactory.createFlow(flowRequest.getFormFlowPath(), flowRequest.getInitData());
			SessionHelper.setFlow(newFormFlow, session);

			String formPath = newFormFlow.navigateToFirstForm(documentHelper);
			writeForm(response, newFormFlow, formPath, flowRequest.isSuppressDebugBar());
		} finally {
			Context.exit();
		}
	}

	public void doActionWriteForm(FormActionRequest formActionRequest, FormFlow formFlow, HttpServletResponse response) throws IOException, FlowExceptionActionError,
			FormSubmissionHelperException, FlowExceptionBadRequest, FlowExceptionFileNotFound, FlowExceptionJavaScript, FlowExceptionXPath, FormParserException, TransformerException {
		Context.enter();
		try {
			formFlow.setRemoteSubmissionHelper(remoteSubmissionHelper);
			formFlow.setSubmissionTimeKeeper(submissionTimeKeeper);
			
			Map<String, String> parameterMap = formActionRequest.getParameterMap();
			FormSubmissionResult submissionResult = formSubmissionHelper.handlePost(formFlow, parameterMap);

			String nextUrl = submissionResult.getNextUrl();
			if (nextUrl != null) {
				writeForm(response, formFlow, nextUrl, formActionRequest.isSuppressDebugBar());
			} else {
				// End of flow. Spit out XML.
				response.setContentType("text/plain");
				response.setHeader("rf.responseType", "data");
				PrintWriter writer = response.getWriter();
				documentHelper.documentToWriterPretty(formFlow.getDataDocument(), writer);
			}
		} finally {
			Context.exit();
		}
	}

	private void writeForm(HttpServletResponse response, FormFlow formFlow, String formPath, boolean suppressDebugBar)
			throws FormParserException, IOException {

		formPath = formFlow.resolveResourcePathIfRelative(formPath);
		String currentFormId = formFlow.getCurrentFormId();
		response.setHeader("rf.formId", currentFormId);
		
		if (!formFlow.isDisableInputsOnSubmit()) {
			response.setHeader("rf.disableInputsOnSubmit", "false");
		}

		InputStream formStream = resourceLoader.getFormResourceAsStream(formPath);
		response.setContentType("text/html");
		formParser.parseForm(formStream, formFlow, response.getWriter(), masterScope, suppressDebugBar);
	}

}
