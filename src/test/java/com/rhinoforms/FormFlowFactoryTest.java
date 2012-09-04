package com.rhinoforms;

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
	private Context jsContext;

	@Before
	public void setup() {
		this.jsContext = Context.enter();
		this.formFlowFactory = new FormFlowFactory();
	}
	
	@Test
	public void testCreateFlow() throws Exception {
		FormFlow formFlow = formFlowFactory.createFlow("src/test/resources/test-flow1.js", jsContext, null);
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
		
		Assert.assertEquals(1, nextForm.getIndexInList());
		nextForm = iterator.next();
		Assert.assertEquals("three", nextForm.getId());
		Assert.assertEquals(2, nextForm.getIndexInList());
		
		List<Form> anotherList = formLists.get("anotherList");
		Assert.assertEquals(1, anotherList.size());
		Form form = anotherList.get(0);
		Assert.assertEquals("editFish", form.getId());
		Assert.assertEquals("fishes/fish[fishIndex]", form.getDocBase());
	}
	
	@After
	public void tearDown() {
		Context.exit();
	}
	
}
