package com.rhinoforms.flow;

import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Document;

import com.rhinoforms.TestResourceLoader;
import com.rhinoforms.TestUtil;
import com.rhinoforms.resourceloader.ResourceLoaderImpl;

public class RemoteSubmissionHelperIntegrationTestManual {
	
	private RemoteSubmissionHelper remoteSubmissionHelper;
	private Document dataDocument;
	private String dataDocumentString;
	private Map<String, String> xsltParameters;
	
	public static void main(String[] args) throws Exception {
		RemoteSubmissionHelperIntegrationTestManual test = new RemoteSubmissionHelperIntegrationTestManual();
		test.run();
	}
	
	public RemoteSubmissionHelperIntegrationTestManual() throws Exception {
		ResourceLoaderImpl resourceLoader = new ResourceLoaderImpl(new TestResourceLoader(), new TestResourceLoader());
		remoteSubmissionHelper = new RemoteSubmissionHelper(resourceLoader);
		dataDocumentString = "<myData><something>aaa</something></myData>";
		dataDocument = TestUtil.createDocument(dataDocumentString);
		xsltParameters = new HashMap<String, String>();
	}
	
	public void run() throws Exception {
		Submission submission = new Submission("http://httpbin.org/post");
		submission.setOmitXmlDeclaration(false);
		submission.setRawXmlRequest(true);
		submission.getData().put("xml", "[dataDocument]");
		remoteSubmissionHelper.handleSubmission(submission, dataDocument, xsltParameters);
	}
	
}
