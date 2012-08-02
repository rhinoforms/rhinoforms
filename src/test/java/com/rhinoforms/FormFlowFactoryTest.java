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
		Assert.assertEquals("myData", formFlow.getDocumentBasePath());
		Map<String, List<Form>> formLists = formFlow.getFormLists();
		Assert.assertEquals(2, formLists.keySet().size());
		List<Form> list = formLists.get("main");
		Assert.assertEquals(3, list.size());
		Iterator<Form> iterator = list.iterator();
		Form next = iterator.next();
		Assert.assertEquals("one", next.getId());
		Assert.assertEquals(0, next.getIndexInList());
		next = iterator.next();
		Assert.assertEquals("two", next.getId());
		Assert.assertEquals(1, next.getIndexInList());
		next = iterator.next();
		Assert.assertEquals("three", next.getId());
		Assert.assertEquals(2, next.getIndexInList());
	}
	
	@After
	public void tearDown() {
		Context.exit();
	}
	
}
