package com.rhinoforms.flow;

import com.rhinoforms.TestApplicationContext;
import com.rhinoforms.TestUtil;
import org.w3c.dom.Document;

import java.util.HashMap;
import java.util.Map;

public class RemoteSubmissionHelperIntegrationTestManual {
	
	private RemoteSubmissionHelper remoteSubmissionHelper;
	private Document dataDocument;
	private String dataDocumentString;
	private Map<String, String> xsltParameters;
	private FormFlow formFlow;
	
	public static void main(String[] args) throws Exception {
		RemoteSubmissionHelperIntegrationTestManual test = new RemoteSubmissionHelperIntegrationTestManual();
		test.run();
	}
	
	public RemoteSubmissionHelperIntegrationTestManual() throws Exception {
		TestApplicationContext applicationContext = new TestApplicationContext();
		remoteSubmissionHelper = applicationContext.getRemoteSubmissionHelper();
		dataDocumentString = "<myData><something>aaa</something></myData>";
		dataDocument = TestUtil.createDocument(dataDocumentString);
		xsltParameters = new HashMap<>();
		formFlow = new FormFlow();
		formFlow.setDataDocument(dataDocument);
	}
	
	public void run() throws Exception {
		Submission submission = new Submission("http://httpbin.org/post");
		submission.setOmitXmlDeclaration(false);
//		submission.setRawXmlRequest(true);
		submission.getData().put("xml", "[dataDocument]");
		submission.getData().put("something", "xpath:/myData/something");
		remoteSubmissionHelper.handleSubmission(submission, xsltParameters, formFlow);
	}
	
}
