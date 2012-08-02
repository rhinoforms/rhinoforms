package com.rhinoforms;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;

public class FormFlowTest {

	private FormFlowFactory formFlowFactory;
	private Context jsContext;

	@Before
	public void setup() {
		this.jsContext = Context.enter();
		this.formFlowFactory = new FormFlowFactory();
	}
	
	@Test
	public void testNavFirstForm() throws Exception {
		FormFlow formFlow = formFlowFactory.createFlow("src/test/resources/test-flow1.js", jsContext, null);
		Assert.assertEquals("one.html", formFlow.navigateToFirstForm());
	}
	
	@Test
	public void testNavNextBack() throws Exception {
		FormFlow formFlow = formFlowFactory.createFlow("src/test/resources/test-flow1.js", jsContext, null);
		Assert.assertEquals("one.html", formFlow.navigateToFirstForm());
		Assert.assertEquals("two.html", formFlow.navigateFlow("next"));
		Assert.assertEquals("one.html", formFlow.navigateFlow("back"));
		Assert.assertEquals("two.html", formFlow.navigateFlow("next"));
		Assert.assertEquals("three.html", formFlow.navigateFlow("next"));
		Assert.assertEquals("two.html", formFlow.navigateFlow("back"));
		Assert.assertEquals("one.html", formFlow.navigateFlow("back"));
		Assert.assertEquals("two.html", formFlow.navigateFlow("next"));
		Assert.assertEquals("three.html", formFlow.navigateFlow("next"));
		Assert.assertEquals(null, formFlow.navigateFlow("finish"));
	}
	
	@Test
	public void testNavSideways() throws Exception {
		FormFlow formFlow = formFlowFactory.createFlow("src/test/resources/test-flow1.js", jsContext, null);
		Assert.assertEquals("one.html", formFlow.navigateToFirstForm());
		Assert.assertEquals("two.html", formFlow.navigateFlow("next"));
		Assert.assertEquals("addSomething.html", formFlow.navigateFlow("add"));
		Assert.assertEquals("two.html", formFlow.navigateFlow("cancel"));
		Assert.assertEquals("addSomething.html", formFlow.navigateFlow("add"));
		Assert.assertEquals("two.html", formFlow.navigateFlow("next"));
		Assert.assertEquals("three.html", formFlow.navigateFlow("next"));
	}
	
	@After
	public void tearDown() {
		Context.exit();
	}
	
}
