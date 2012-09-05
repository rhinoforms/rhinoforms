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
	private DocumentHelper documentHelper;

	@Before
	public void setup() throws IOException, FormFlowFactoryException {
		this.jsContext = Context.enter();
		this.formFlowFactory = new FormFlowFactory(new TestResourceLoader());
		this.actionParams = new HashMap<String, String>();
		this.formFlow = formFlowFactory.createFlow("test-flow1.js", jsContext, "<myData/>");
		this.documentHelper = new DocumentHelper();
	}

	@Test
	public void testNavFirstForm() throws Exception {
		Assert.assertEquals("one.html", formFlow.navigateToFirstForm(documentHelper));
	}

	@Test
	public void testNavNextBack() throws Exception {
		Assert.assertEquals("one.html", formFlow.navigateToFirstForm(documentHelper));
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams, documentHelper));
		Assert.assertEquals("one.html", formFlow.doAction("back", actionParams, documentHelper));
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams, documentHelper));
		Assert.assertEquals("three.html", formFlow.doAction("next", actionParams, documentHelper));
		Assert.assertEquals("two.html", formFlow.doAction("back", actionParams, documentHelper));
		Assert.assertEquals("one.html", formFlow.doAction("back", actionParams, documentHelper));
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams, documentHelper));
		Assert.assertEquals("three.html", formFlow.doAction("next", actionParams, documentHelper));
		Assert.assertEquals(null, formFlow.doAction("finish", actionParams, documentHelper));
	}

	@Test
	public void testNavSideways() throws Exception {
		Assert.assertEquals("one.html", formFlow.navigateToFirstForm(documentHelper));
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams, documentHelper));
		Assert.assertEquals("editFish.html", formFlow.doAction("add", actionParams, documentHelper));
		Assert.assertEquals("two.html", formFlow.doAction("cancel", actionParams, documentHelper));
		Assert.assertEquals("editFish.html", formFlow.doAction("add", actionParams, documentHelper));
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams, documentHelper));
		Assert.assertEquals("three.html", formFlow.doAction("next", actionParams, documentHelper));
	}

	@Test
	public void testNavDocBaseWithNext() throws Exception {
		Assert.assertEquals("one.html", formFlow.navigateToFirstForm(documentHelper));
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams, documentHelper));

		Assert.assertEquals("/myData", formFlow.getCurrentDocBase());
		Assert.assertEquals("editFish.html", formFlow.doAction("add", actionParams, documentHelper));
		Assert.assertEquals("/myData/fishes/fish[1]", formFlow.getCurrentDocBase());
	}

	@Test
	public void testNavDocBaseWithRelativeIndexAndNext() throws Exception {
		Assert.assertEquals("one.html", formFlow.navigateToFirstForm(documentHelper));
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams, documentHelper));

		Assert.assertEquals("/myData", formFlow.getCurrentDocBase());
		actionParams.put("fishIndex", "2");
		Assert.assertEquals("editFish.html", formFlow.doAction("edit", actionParams, documentHelper));
		Assert.assertEquals("/myData/fishes/fish[2]", formFlow.getCurrentDocBase());
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams, documentHelper));
		Assert.assertEquals("/myData", formFlow.getCurrentDocBase());
	}

	@Test
	public void testNavDocBaseWithRelativeIndexAndCancel() throws Exception {
		Assert.assertEquals("one.html", formFlow.navigateToFirstForm(documentHelper));
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams, documentHelper));

		Assert.assertEquals("/myData", formFlow.getCurrentDocBase());
		actionParams.put("fishIndex", "2");
		Assert.assertEquals("editFish.html", formFlow.doAction("edit", actionParams, documentHelper));
		Assert.assertEquals("/myData/fishes/fish[2]", formFlow.getCurrentDocBase());
		Assert.assertEquals("two.html", formFlow.doAction("cancel", actionParams, documentHelper));
		Assert.assertEquals("/myData", formFlow.getCurrentDocBase());
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

	@Test
	public void testNavDocBaseWithIndexTwoDeep() throws Exception {
		Assert.assertEquals("one.html", formFlow.navigateToFirstForm(documentHelper));
		Assert.assertEquals("<myData/>", documentHelper.documentToString(formFlow.getDataDocument()));
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams, documentHelper));

		actionParams.put("fishIndex", "1");
		Assert.assertEquals("editFish.html", formFlow.doAction("edit", actionParams, documentHelper));
		Assert.assertEquals("/myData/fishes/fish[1]", formFlow.getCurrentDocBase());
		Assert.assertEquals("<myData><fishes><fish/></fishes></myData>", documentHelper.documentToString(formFlow.getDataDocument()));

		Assert.assertEquals("editGill.html", formFlow.doAction("addGill", actionParams, documentHelper));
		Assert.assertEquals("/myData/fishes/fish[1]/gills/gill[1]", formFlow.getCurrentDocBase());
		Assert.assertEquals("<myData><fishes><fish><gills><gill/></gills></fish></fishes></myData>", documentHelper.documentToString(formFlow.getDataDocument()));

		Assert.assertEquals("editFish.html", formFlow.doAction("next", actionParams, documentHelper));
		Assert.assertEquals("/myData/fishes/fish[1]", formFlow.getCurrentDocBase());
		Assert.assertEquals("<myData><fishes><fish/></fishes></myData>", documentHelper.documentToString(formFlow.getDataDocument()));

		Assert.assertEquals("editGill.html", formFlow.doAction("addGill", actionParams, documentHelper));
		Assert.assertEquals("/myData/fishes/fish[1]/gills/gill[1]", formFlow.getCurrentDocBase());
		
		Assert.assertEquals("editFish.html", formFlow.doAction("cancel", actionParams, documentHelper));
		Assert.assertEquals("/myData/fishes/fish[1]", formFlow.getCurrentDocBase());
	}

	@Test
	public void testNavDocBaseWithSecondIndexTwoDeep() throws Exception {
		this.formFlow = formFlowFactory.createFlow("test-flow1.js", jsContext, "<myData><fishes><fish><something>existing data</something></fish></fishes></myData>");
		Assert.assertEquals("one.html", formFlow.navigateToFirstForm(documentHelper));
		Assert.assertEquals("<myData><fishes><fish><something>existing data</something></fish></fishes></myData>", documentHelper.documentToString(formFlow.getDataDocument()));
		Assert.assertEquals("two.html", formFlow.doAction("next", actionParams, documentHelper));

		actionParams.put("fishIndex", "2");
		Assert.assertEquals("editFish.html", formFlow.doAction("edit", actionParams, documentHelper));
		Assert.assertEquals("/myData/fishes/fish[2]", formFlow.getCurrentDocBase());
		Assert.assertEquals("<myData><fishes><fish><something>existing data</something></fish><fish/></fishes></myData>", documentHelper.documentToString(formFlow.getDataDocument()));
	}

	@After
	public void tearDown() {
		Context.exit();
	}

}
