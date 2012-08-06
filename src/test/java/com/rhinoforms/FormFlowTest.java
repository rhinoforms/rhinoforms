package com.rhinoforms;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;

public class FormFlowTest {

	private FormFlowFactory formFlowFactory;
	private Context jsContext;
	private HashMap<String, String> actionParams;
	private FormFlow formFlow;

	@Before
	public void setup() throws IOException, FormFlowFactoryException {
		this.jsContext = Context.enter();
		this.formFlowFactory = new FormFlowFactory();
		this.actionParams = new HashMap<String, String>();
		this.formFlow = formFlowFactory.createFlow("src/test/resources/test-flow1.js", jsContext, "<myData/>");
	}
	
	@Test
	public void testNavFirstForm() throws Exception {
		Assert.assertEquals("one.html", formFlow.navigateToFirstForm());
	}
	
	@Test
	public void testNavNextBack() throws Exception {
		Assert.assertEquals("one.html", formFlow.navigateToFirstForm());
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams));
		Assert.assertEquals("one.html", formFlow.doAction("back", actionParams));
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams));
		Assert.assertEquals("three.html", formFlow.doAction("next", actionParams));
		Assert.assertEquals("two.html", formFlow.doAction("back", actionParams));
		Assert.assertEquals("one.html", formFlow.doAction("back", actionParams));
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams));
		Assert.assertEquals("three.html", formFlow.doAction("next", actionParams));
		Assert.assertEquals("four.html", formFlow.doAction("next", actionParams));
		Assert.assertEquals(null, formFlow.doAction("finish", actionParams));
	}
	
	@Test
	public void testNavSideways() throws Exception {
		Assert.assertEquals("one.html", formFlow.navigateToFirstForm());
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams));
		Assert.assertEquals("addSomething.html", formFlow.doAction("add", actionParams));
		Assert.assertEquals("two.html", formFlow.doAction("cancel", actionParams));
		Assert.assertEquals("addSomething.html", formFlow.doAction("add", actionParams));
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams));
		Assert.assertEquals("three.html", formFlow.doAction("next", actionParams));
	}
	
	@Test
	public void testNavDocBaseWithNext() throws Exception {
		Assert.assertEquals("one.html", formFlow.navigateToFirstForm());
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams));
		Assert.assertEquals("three.html", formFlow.doAction("next", actionParams));
		
		Assert.assertEquals("/myData", formFlow.getDocBase());
		Assert.assertEquals("editFish.html", formFlow.doAction("add", actionParams));
		Assert.assertEquals("/myData/fishes/fish[1]", formFlow.getDocBase());
	}
	
	@Test
	public void testNavDocBaseWithIndex() throws Exception {
		Assert.assertEquals("one.html", formFlow.navigateToFirstForm());
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams));
		Assert.assertEquals("three.html", formFlow.doAction("next", actionParams));
		
		Assert.assertEquals("/myData", formFlow.getDocBase());
		actionParams.put("index", "2");
		Assert.assertEquals("editFish.html", formFlow.doAction("edit", actionParams));
		Assert.assertEquals("/myData/fishes/fish[2]", formFlow.getDocBase());
	}
	
	@Test
	public void testFilterActionParams() throws Exception {
		HashMap<String, String> paramsFromFlowAction = new HashMap<String, String>();
		paramsFromFlowAction.put("pondIndex", "?");
		paramsFromFlowAction.put("fishIndex", "next");
		
		HashMap<String, String> paramsFromFontend = new HashMap<String, String>();
		paramsFromFontend.put("pondIndex", "2");
		paramsFromFontend.put("fishIndex", "3");
		paramsFromFontend.put("anotherIndex", "5");
		
		Map<String, String> filteredActionParams = formFlow.filterActionParams(paramsFromFontend, paramsFromFlowAction);
		
		Assert.assertEquals("Questionmark param has been injected", "2", filteredActionParams.get("pondIndex"));
		Assert.assertEquals("Already set param has not been overridden ", "next", filteredActionParams.get("fishIndex"));
		Assert.assertEquals("No extra params have got through from the frontend", 2, filteredActionParams.size());
	}
	
	@After
	public void tearDown() {
		Context.exit();
	}
	
}
