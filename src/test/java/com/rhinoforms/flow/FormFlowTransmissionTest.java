package com.rhinoforms.flow;

import java.util.HashMap;

import javax.xml.transform.TransformerException;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.w3c.dom.Document;

import com.rhinoforms.ApplicationContext;
import com.rhinoforms.TestApplicationContext;
import com.rhinoforms.TestUtil;
import com.rhinoforms.xml.DocumentHelper;

public class FormFlowTransmissionTest {

	private FormFlowFactory formFlowFactory;
	private TransformHelper transformHelper;
	private HashMap<String, String> actionParams;
	private FormFlow formFlow;
	private DocumentHelper documentHelper;
	private Document dataDocument;
	private String dataDocumentString;

	@Before
	public void setup() throws Exception {
		this.actionParams = new HashMap<String, String>();
		this.documentHelper = new DocumentHelper();
		
		dataDocumentString = "<myData><toCopy>Pre transform string</toCopy><another>anotherVal</another></myData>";
		dataDocument = TestUtil.createDocument(dataDocumentString);
		
		
		
		Context.enter();
		ApplicationContext applicationContext = new TestApplicationContext();
		transformHelper = applicationContext.getTransformHelper();
		this.formFlowFactory = applicationContext.getFormFlowFactory();
		this.formFlow = formFlowFactory.createFlow("test-flow1-submission-transform.js", "<myData/>");
		this.formFlow.setDataDocument(dataDocument);
		this.formFlow.setTransformHelper(transformHelper);
	}
	
	@After
	public void after() {
		Context.exit();
	}

	@Test
	public void testNavFirstForm() throws Exception {
		Assert.assertEquals("zero.html", formFlow.navigateToFirstForm(documentHelper));
	}

	@Test
	public void testNavWithTransform() throws Exception {
		Assert.assertEquals("zero.html", formFlow.navigateToFirstForm(documentHelper));
		
		Assert.assertEquals("one.html", formFlow.doAction("next", actionParams, documentHelper));
		Assert.assertEquals(dataDocumentString, getDataDocString());
		
		Assert.assertEquals("one.html", formFlow.doAction("transform", actionParams, documentHelper));
		Assert.assertEquals("<myData><outerNode><copy>Pre transform string</copy><new>Post transform addition</new></outerNode></myData>", getDataDocString());
		
		Assert.assertEquals("one.html", formFlow.doAction("cancel-back-to-one", actionParams, documentHelper));
	}
	
	private String getDataDocString() throws TransformerException{
		return documentHelper.documentToString(formFlow.getDataDocument());
	}
}
