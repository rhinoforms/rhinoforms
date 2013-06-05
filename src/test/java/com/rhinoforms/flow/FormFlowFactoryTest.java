package com.rhinoforms.flow;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;

import com.rhinoforms.ApplicationContext;
import com.rhinoforms.TestApplicationContext;
import com.rhinoforms.resourceloader.ResourceLoaderException;

public class FormFlowFactoryTest {

	private FormFlowFactory formFlowFactory;

	@Before
	public void setup() throws IOException, ResourceLoaderException {
		Context.enter();
		ApplicationContext applicationContext = new TestApplicationContext();
		this.formFlowFactory = applicationContext.getFormFlowFactory();
	}
	
	@After
	public void after() {
		Context.exit();
	}
	
	@Test
	public void testCreateFlow() throws Exception {
		FormFlow formFlow = formFlowFactory.createFlow("test-flow1.js", null);
		Map<String, List<Form>> formLists = formFlow.getFormLists();
		Assert.assertEquals(4, formLists.keySet().size());
		List<Form> list = formLists.get("main");
		Assert.assertEquals(3, list.size());
		Iterator<Form> iterator = list.iterator();
		Form nextForm = iterator.next();
		Assert.assertEquals("one", nextForm.getId());
		Assert.assertEquals(0, nextForm.getIndexInList());
		nextForm = iterator.next();
		Assert.assertEquals("two", nextForm.getId());
		Map<String, FlowAction> actions = nextForm.getActions();
		Assert.assertEquals(5, actions.size());
		FlowAction flowAction = actions.get("add");
		Map<String, String> params = flowAction.getParams();
		Assert.assertEquals(1, params.size());
		Assert.assertEquals("next", params.get("fishIndex"));
		Assert.assertNull(flowAction.getSubmissions());
		
		Assert.assertEquals(1, nextForm.getIndexInList());
		nextForm = iterator.next();
		Assert.assertEquals("three", nextForm.getId());
		Assert.assertEquals(2, nextForm.getIndexInList());
		actions = nextForm.getActions();
		FlowAction flowActionToServer = actions.get("send-to-my-server");
		Assert.assertNotNull(flowActionToServer);
		
		List<Submission> submissions = flowActionToServer.getSubmissions();
		Assert.assertEquals(3, submissions.size());
		Submission submission = submissions.get(0);
		Assert.assertNotNull(submission);
		Assert.assertEquals("Url injected from properties file", "http://localhost/dummy-url", submission.getUrl());
		Assert.assertEquals("POST", submission.getMethod());
		Map<String, String> data = submission.getData();
		Assert.assertEquals(2, data.size());
		Assert.assertEquals("10", data.get("type"));
		Assert.assertEquals("[dataDocument]", data.get("paramA"));
		Assert.assertEquals("/myData/submissionResult", submission.getResultInsertPoint());
		Assert.assertEquals("xslt/toServerFormat.xsl", submission.getPreTransform());
		Assert.assertEquals("xslt/fromServerFormat.xsl", submission.getPostTransform());
		Assert.assertEquals("Could not reach the web service.", submission.getMessageOnHttpError());
		Assert.assertTrue(flowActionToServer.isClearTargetFormDocBase());
		
		Submission submission2 = submissions.get(1);
		Assert.assertEquals(0, submission2.getData().size());
		
		FlowAction cancelAction = actions.get("cancel-back-to-one");
		Assert.assertEquals(FlowActionType.CANCEL, cancelAction.getType());
		Assert.assertEquals("one", cancelAction.getTarget());
		
		List<Form> anotherList = formLists.get("anotherList");
		Assert.assertEquals(1, anotherList.size());
		Form form = anotherList.get(0);
		Assert.assertEquals("editFish", form.getId());
		Assert.assertEquals("fishes/fish[fishIndex]", form.getDocBase());
		Assert.assertNull(form.getActions().get("cancel").getSubmissions());
		List<String> libraries = formFlow.getLibraries();
		Assert.assertEquals(1, libraries.size());
		Assert.assertEquals("js/testUtil.js", libraries.get(0));
		
		List<Form> indexTestList = formLists.get("indexTestList");
		Form indexTestA = indexTestList.get(0);
		Map<String, FlowAction> indexTestActions = indexTestA.getActions();
		FlowAction indexTestAction = indexTestActions.get("next");
		Assert.assertNotNull(indexTestAction);
		Assert.assertEquals(FlowActionType.NEXT, indexTestAction.getType());
		Assert.assertEquals("next", indexTestAction.getParams().get("fishIndex"));
	}
	
	@Test
	public void testCreateFlowMinSubmission() throws Exception {
		FormFlow formFlow = formFlowFactory.createFlow("test-flow1-submission-min.js", null);
		Map<String, List<Form>> formLists = formFlow.getFormLists();
		List<Form> list = formLists.get("main");
		Iterator<Form> iterator = list.iterator();
		Form nextForm = iterator.next();
		Map<String, FlowAction> actions = nextForm.getActions();
		FlowAction flowActionToServer = actions.get("sendToMyServer");
		Assert.assertNotNull(flowActionToServer);
		
		Submission submission = flowActionToServer.getSubmissions().get(0);
		Assert.assertNotNull(submission);
		Assert.assertEquals("http://localhost/dummy-url", submission.getUrl());
		Assert.assertEquals("POST", submission.getMethod());
		Assert.assertEquals(false, submission.isRawXmlRequest());
		Map<String, String> data = submission.getData();
		Assert.assertEquals(2, data.size());
		Assert.assertEquals("10", data.get("type"));
		Assert.assertEquals("[dataDocument]", data.get("paramA"));
		Assert.assertEquals("/myData/submissionResult", submission.getResultInsertPoint());
		Assert.assertEquals(null, submission.getPreTransform());
		Assert.assertEquals(null, submission.getPostTransform());
	}
	
	@Test
	public void testCreateFlowSubmissionWithXpathUrl() throws Exception {
		FormFlow formFlow = formFlowFactory.createFlow("test-flow1-submission-with-xpath-url.js", null);
		Map<String, List<Form>> formLists = formFlow.getFormLists();
		List<Form> list = formLists.get("main");
		Iterator<Form> iterator = list.iterator();
		Form nextForm = iterator.next();
		Map<String, FlowAction> actions = nextForm.getActions();
		FlowAction flowActionToServer = actions.get("sendToMyServer");
		Assert.assertNotNull(flowActionToServer);
		
		Submission submission = flowActionToServer.getSubmissions().get(0);
		Assert.assertNotNull(submission);
		Assert.assertEquals("http://localhost/service/REST/{{//calcRef}}", submission.getUrl());
	}
	
	@Test
	public void testCreateFlowRawSubmission() throws Exception {
		FormFlow formFlow = formFlowFactory.createFlow("test-flow1-submission-raw.js", null);
		Map<String, List<Form>> formLists = formFlow.getFormLists();
		List<Form> list = formLists.get("main");
		Iterator<Form> iterator = list.iterator();
		Form nextForm = iterator.next();
		Map<String, FlowAction> actions = nextForm.getActions();
		FlowAction flowActionToServer = actions.get("sendToMyServer");
		Assert.assertNotNull(flowActionToServer);
		
		Submission submission = flowActionToServer.getSubmissions().get(0);
		Assert.assertNotNull(submission);
		Assert.assertEquals("http://localhost/dummy-url", submission.getUrl());
		Assert.assertEquals("POST", submission.getMethod());
		Assert.assertEquals(true, submission.isRawXmlRequest());
		Map<String, String> data = submission.getData();
		Assert.assertTrue(data.isEmpty());
		Assert.assertEquals("/myData/submissionResult", submission.getResultInsertPoint());
		Assert.assertEquals(null, submission.getPreTransform());
		Assert.assertEquals(null, submission.getPostTransform());
	}
	
}
