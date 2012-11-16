package com.rhinoforms.flow;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import com.rhinoforms.TestConnectionFactory;
import com.rhinoforms.TestResourceLoader;
import com.rhinoforms.TestUtil;
import com.rhinoforms.resourceloader.ResourceLoaderImpl;
import com.rhinoforms.xml.DocumentHelper;

public class RemoteSubmissionHelperTest {
	
	private RemoteSubmissionHelper remoteSubmissionHelper;
	private Document dataDocument;
	private DocumentHelper documentHelper;
	private TestConnectionFactory testConnectionFactory;
	private String dataDocumentString;
	private Map<String, String> xsltParameters;
	
	@Before
	public void setup() throws Exception {
		documentHelper = new DocumentHelper();
		ResourceLoaderImpl resourceLoader = new ResourceLoaderImpl(new TestResourceLoader(), new TestResourceLoader());
		remoteSubmissionHelper = new RemoteSubmissionHelper(resourceLoader, "");
		testConnectionFactory = new TestConnectionFactory();
		remoteSubmissionHelper.setConnectionFactory(testConnectionFactory);
		dataDocumentString = "<myData><something>a</something></myData>";
		dataDocument = TestUtil.createDocument(dataDocumentString);
		xsltParameters = new HashMap<String, String>();
	}
	
	@Test
	public void testSimplestHandleSubmission() throws Exception {
		Submission submission = new Submission("http://localhost/dummyURL");
		submission.setOmitXmlDeclaration(true);
		submission.getData().put("xml", "[dataDocument]");
		testConnectionFactory.setResultXmlString("<submissionResult>one</submissionResult>");
		
		remoteSubmissionHelper.handleSubmission(submission, dataDocument, xsltParameters);
		
		Assert.assertEquals("application/x-www-form-urlencoded", testConnectionFactory.getRequestProperties().get("Content-Type"));
		String submittedData = new String(testConnectionFactory.getByteArrayOutputStream().toByteArray());
		Assert.assertEquals("xml=<myData><something>a</something></myData>", URLDecoder.decode(submittedData, "UTF-8"));
		String dataDocumentStringAfterSubmission = documentHelper.documentToString(dataDocument);
		Assert.assertEquals("<myData><something>a</something></myData>", dataDocumentStringAfterSubmission);
	}
	
	@Test
	public void testSimplestHandleSubmissionWithXmlDeclaration() throws Exception {
		Submission submission = new Submission("http://localhost/dummyURL");
		submission.getData().put("xml", "[dataDocument]");
		testConnectionFactory.setResultXmlString("<submissionResult>one</submissionResult>");
		
		remoteSubmissionHelper.handleSubmission(submission, dataDocument, xsltParameters);
		
		Assert.assertEquals("application/x-www-form-urlencoded", testConnectionFactory.getRequestProperties().get("Content-Type"));
		String submittedData = new String(testConnectionFactory.getByteArrayOutputStream().toByteArray());
		Assert.assertEquals("xml=<?xml version=\"1.0\" encoding=\"UTF-8\"?><myData><something>a</something></myData>", URLDecoder.decode(submittedData, "UTF-8"));
		String dataDocumentStringAfterSubmission = documentHelper.documentToString(dataDocument);
		Assert.assertEquals("<myData><something>a</something></myData>", dataDocumentStringAfterSubmission);
	}
	
	@Test
	public void testHandleRawSubmissionWithXmlDeclaration() throws Exception {
		Submission submission = new Submission("http://localhost/dummyURL");
		submission.setRawXmlRequest(true);
		testConnectionFactory.setResultXmlString("<submissionResult>one</submissionResult>");
		
		remoteSubmissionHelper.handleSubmission(submission, dataDocument, xsltParameters);
		
		Assert.assertEquals("application/xml", testConnectionFactory.getRequestProperties().get("Content-Type"));
		String submittedData = new String(testConnectionFactory.getByteArrayOutputStream().toByteArray());
		Assert.assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><myData><something>a</something></myData>", URLDecoder.decode(submittedData, "UTF-8"));
		String dataDocumentStringAfterSubmission = documentHelper.documentToString(dataDocument);
		Assert.assertEquals("<myData><something>a</something></myData>", dataDocumentStringAfterSubmission);
	}
	
	@Test
	public void testHandleSubmissionInsertResult() throws Exception {
		Submission submission = new Submission("http://localhost/dummyURL");
		submission.setOmitXmlDeclaration(true);
		submission.getData().put("xml", "[dataDocument]");
		submission.setResultInsertPoint("/myData/submissionResult");
		testConnectionFactory.setResultXmlString("<data>one</data>");
		
		remoteSubmissionHelper.handleSubmission(submission, dataDocument, xsltParameters);
		
		Assert.assertEquals("application/x-www-form-urlencoded", testConnectionFactory.getRequestProperties().get("Content-Type"));
		String submittedData = new String(testConnectionFactory.getByteArrayOutputStream().toByteArray());
		Assert.assertEquals("xml=" + dataDocumentString, URLDecoder.decode(submittedData, "UTF-8"));
		String dataDocumentStringAfterSubmission = documentHelper.documentToString(dataDocument);
		Assert.assertEquals("<myData><something>a</something><submissionResult><data>one</data></submissionResult></myData>", dataDocumentStringAfterSubmission);
	}
	
	@Test
	public void testHandleSubmissionInsertResultReplaceExisting() throws Exception {
		Submission submission = new Submission("http://localhost/dummyURL");
		submission.setOmitXmlDeclaration(true);
		submission.getData().put("xml", "[dataDocument]");
		submission.setResultInsertPoint("/myData/submissionResult");
		testConnectionFactory.setResultXmlString("<data>one</data>");
		
		remoteSubmissionHelper.handleSubmission(submission, dataDocument, xsltParameters);
		
		Assert.assertEquals("application/x-www-form-urlencoded", testConnectionFactory.getRequestProperties().get("Content-Type"));
		String submittedData = new String(testConnectionFactory.getByteArrayOutputStream().toByteArray());
		Assert.assertEquals("xml=" + dataDocumentString, URLDecoder.decode(submittedData, "UTF-8"));
		String dataDocumentStringAfterSubmission = documentHelper.documentToString(dataDocument);
		Assert.assertEquals("<myData><something>a</something><submissionResult><data>one</data></submissionResult></myData>", dataDocumentStringAfterSubmission);
		
		testConnectionFactory.resetByteArrayOutputStream();
		remoteSubmissionHelper.handleSubmission(submission, dataDocument, xsltParameters);
		
		submittedData = new String(testConnectionFactory.getByteArrayOutputStream().toByteArray());
		dataDocumentStringAfterSubmission = documentHelper.documentToString(dataDocument);
		Assert.assertEquals("<myData><something>a</something><submissionResult><data>one</data></submissionResult></myData>", dataDocumentStringAfterSubmission);
	}
	
	@Test
	public void testHandleSubmissionBadResult() {
		Submission submission = new Submission("http://localhost/dummyURL");
		submission.setOmitXmlDeclaration(true);
		submission.getData().put("xml", "[dataDocument]");
		submission.setResultInsertPoint("/myData");
		testConnectionFactory.setResultXmlString("<submissionResult>one</submissionResult>");
		testConnectionFactory.setTestResponseCode(500);
		testConnectionFactory.setTestResponseMessage("Something went very wrong.");
		
		try {
			remoteSubmissionHelper.handleSubmission(submission, dataDocument, xsltParameters);
			Assert.fail("Should have thrown Exception");
		} catch (RemoteSubmissionHelperException e) {
			// Pass
			Assert.assertEquals("application/x-www-form-urlencoded", testConnectionFactory.getRequestProperties().get("Content-Type"));
			Assert.assertEquals("Bad response from target service. Status:500, message:Something went very wrong.", e.getMessage());
		}
	}
	
	@Test
	public void testHandleSubmissionPreTransform() throws Exception {
		Submission submission = new Submission("http://localhost/dummyURL");
		submission.setOmitXmlDeclaration(true);
		submission.setPreTransform("xslt/toServerFormat.xsl");
		submission.getData().put("xml", "[dataDocument]");
		submission.setResultInsertPoint("/myData/submissionResult");
		testConnectionFactory.setResultXmlString("<data>one</data>");
		
		remoteSubmissionHelper.handleSubmission(submission, dataDocument, xsltParameters);
		
		Assert.assertEquals("application/x-www-form-urlencoded", testConnectionFactory.getRequestProperties().get("Content-Type"));
		String submittedData = new String(testConnectionFactory.getByteArrayOutputStream().toByteArray());
		Assert.assertEquals("xml=<serverData><abc>a</abc></serverData>", URLDecoder.decode(submittedData, "UTF-8"));
		String dataDocumentStringAfterSubmission = documentHelper.documentToString(dataDocument);
		Assert.assertEquals("<myData><something>a</something><submissionResult><data>one</data></submissionResult></myData>", dataDocumentStringAfterSubmission);
	}
	
	@Test
	public void testHandleSubmissionPreTransformWithXmlDeclaration() throws Exception {
		Submission submission = new Submission("http://localhost/dummyURL");
		submission.setPreTransform("xslt/toServerFormat.xsl");
		submission.getData().put("xml", "[dataDocument]");
		submission.setResultInsertPoint("/myData/submissionResult");
		testConnectionFactory.setResultXmlString("<data>one</data>");
		
		remoteSubmissionHelper.handleSubmission(submission, dataDocument, xsltParameters);
		
		Assert.assertEquals("application/x-www-form-urlencoded", testConnectionFactory.getRequestProperties().get("Content-Type"));
		String submittedData = new String(testConnectionFactory.getByteArrayOutputStream().toByteArray());
		Assert.assertEquals("xml=<?xml version=\"1.0\" encoding=\"UTF-8\"?><serverData><abc>a</abc></serverData>", URLDecoder.decode(submittedData, "UTF-8"));
		String dataDocumentStringAfterSubmission = documentHelper.documentToString(dataDocument);
		Assert.assertEquals("<myData><something>a</something><submissionResult><data>one</data></submissionResult></myData>", dataDocumentStringAfterSubmission);
	}
	
	@Test
	public void testHandleSubmissionPostTransform() throws Exception {
		Submission submission = new Submission("http://localhost/dummyURL");
		submission.setOmitXmlDeclaration(true);
		submission.getData().put("myXml", "[dataDocument]");
		submission.setPostTransform("xslt/fromServerFormat.xsl");
		submission.setResultInsertPoint("/myData/submissionResult");
		testConnectionFactory.setResultXmlString("<serverData><wrapper><premium123>10.00</premium123></wrapper></serverData>");
		
		remoteSubmissionHelper.handleSubmission(submission, dataDocument, xsltParameters);
		
		Assert.assertEquals("application/x-www-form-urlencoded", testConnectionFactory.getRequestProperties().get("Content-Type"));
		String submittedData = new String(testConnectionFactory.getByteArrayOutputStream().toByteArray());
		Assert.assertEquals("myXml=" + dataDocumentString, URLDecoder.decode(submittedData, "UTF-8"));
		String dataDocumentStringAfterSubmission = documentHelper.documentToString(dataDocument);
		Assert.assertEquals("<myData><something>a</something><submissionResult><submissionResult><premium>10.00</premium></submissionResult></submissionResult></myData>", dataDocumentStringAfterSubmission);
	}
	
}
