package com.rhinoforms;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;

public class FormFlowFactoryTest {

	private FormFlowFactory formFlowFactory;
	private JSMasterScope masterScope;

	@Before
	public void setup() throws IOException {
		TestResourceLoader resourceLoader = new TestResourceLoader();
		Context jsContext = Context.enter();
		this.masterScope = new RhinoFormsMasterScopeFactory().createMasterScope(jsContext, resourceLoader);
		this.formFlowFactory = new FormFlowFactory(new TestResourceLoader(), this.masterScope);
	}
	
	@After
	public void after() {
		Context.exit();
	}
	
	@Test
	public void testCreateFlow() throws Exception {
		FormFlow formFlow = formFlowFactory.createFlow("test-flow1.js", null);
		Map<String, List<Form>> formLists = formFlow.getFormLists();
		Assert.assertEquals(3, formLists.keySet().size());
		List<Form> list = formLists.get("main");
		Assert.assertEquals(3, list.size());
		Iterator<Form> iterator = list.iterator();
		Form nextForm = iterator.next();
		Assert.assertEquals("one", nextForm.getId());
		Assert.assertEquals(0, nextForm.getIndexInList());
		nextForm = iterator.next();
		Assert.assertEquals("two", nextForm.getId());
		Map<String, FlowAction> actions = nextForm.getActions();
		Assert.assertEquals(4, actions.size());
		FlowAction flowAction = actions.get("add");
		Map<String, String> params = flowAction.getParams();
		Assert.assertEquals(1, params.size());
		Assert.assertEquals("next", params.get("fishIndex"));
		Assert.assertNull(flowAction.getSubmission());
		
		Assert.assertEquals(1, nextForm.getIndexInList());
		nextForm = iterator.next();
		Assert.assertEquals("three", nextForm.getId());
		Assert.assertEquals(2, nextForm.getIndexInList());
		actions = nextForm.getActions();
		FlowAction flowActionToServer = actions.get("sendToMyServer");
		Assert.assertNotNull(flowActionToServer);
		
		Submission submission = flowActionToServer.getSubmission();
		Assert.assertNotNull(submission);
		Assert.assertEquals("http://localhost/dummy-url", submission.getUrl());
		Assert.assertEquals("POST", submission.getMethod());
		Map<String, String> data = submission.getData();
		Assert.assertEquals(2, data.size());
		Assert.assertEquals("10", data.get("type"));
		Assert.assertEquals("[dataDocument]", data.get("paramA"));
		Assert.assertEquals("/myData/submissionResult", submission.getResultInsertPoint());
		Assert.assertEquals("xslt/toServerFormat.xsl", submission.getPreTransform());
		Assert.assertEquals("xslt/fromServerFormat.xsl", submission.getPostTransform());
		
		List<Form> anotherList = formLists.get("anotherList");
		Assert.assertEquals(1, anotherList.size());
		Form form = anotherList.get(0);
		Assert.assertEquals("editFish", form.getId());
		Assert.assertEquals("fishes/fish[fishIndex]", form.getDocBase());
		Assert.assertNull(form.getActions().get("cancel").getSubmission());
		List<String> libraries = formFlow.getLibraries();
		Assert.assertEquals(1, libraries.size());
		Assert.assertEquals("js/testUtil.js", libraries.get(0));
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
		
		Submission submission = flowActionToServer.getSubmission();
		Assert.assertNotNull(submission);
		Assert.assertEquals("http://localhost/dummy-url", submission.getUrl());
		Assert.assertEquals("POST", submission.getMethod());
		Map<String, String> data = submission.getData();
		Assert.assertEquals(2, data.size());
		Assert.assertEquals("10", data.get("type"));
		Assert.assertEquals("[dataDocument]", data.get("paramA"));
		Assert.assertEquals("/myData/submissionResult", submission.getResultInsertPoint());
		Assert.assertEquals(null, submission.getPreTransform());
		Assert.assertEquals(null, submission.getPostTransform());
	}
	
}
