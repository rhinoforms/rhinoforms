package com.rhinoforms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import com.rhinoforms.js.NetUtil;
import com.rhinoforms.serverside.InputPojo;

public class FormSubmissionHelperTest {

	private Context context;
	private JSMasterScope masterScope;
	private FormSubmissionHelper formSubmissionHelper;
	private Object netUtilReturnObject;
	private String netUtilUrlRequested;
	
	@Before
	public void before() throws Exception {
		context = Context.enter();
		RhinoFormsMasterScopeFactory masterScopeFactory = new RhinoFormsMasterScopeFactory();
		
		// Hacktastic NetUtil implementation for unit-test
		masterScopeFactory.setNetUtil(new NetUtil() {
			@Override
			public Object httpGetJsObject(String urlString) throws IOException {
				netUtilUrlRequested = urlString;
				return netUtilReturnObject;
			}
		});
		
		masterScope = masterScopeFactory.createMasterScope(context, new TestResourceLoader());
		formSubmissionHelper = new FormSubmissionHelper(masterScope);
	}
	
	@After
	public void after() {
		Context.exit();
	}
	
	@Test
	public void testValidationKeyword_required() throws Exception {
		ArrayList<InputPojo> inputs = new ArrayList<InputPojo>();
		HashMap<String, String> rfAttributes = new HashMap<String, String>();
		rfAttributes.put(Constants.VALIDATION_ATTR, "required");
		InputPojo married = new InputPojo("name", "text", rfAttributes);
		married.setValue("");
		inputs.add(married);
		
		Set<String> fieldsInError = formSubmissionHelper.validateInput(inputs);
		Assert.assertEquals(1, fieldsInError.size());
		Assert.assertEquals("name", fieldsInError.iterator().next());
		
		married.setValue("Kai");
		Assert.assertEquals(0, formSubmissionHelper.validateInput(inputs).size());
	}
	
	@Test
	public void testValidationKeyword_fromSource() throws Exception {
		
		ArrayList<InputPojo> inputs = new ArrayList<InputPojo>();
		HashMap<String, String> rfAttributes = new HashMap<String, String>();
		rfAttributes.put(Constants.SELECT_SOURCE_ATTR, "http://somewhere/something?value={value}");
		rfAttributes.put(Constants.VALIDATION_ATTR, "fromSource");
		InputPojo occupation = new InputPojo("occupation", "text", rfAttributes);
		inputs.add(occupation);
		
		Scriptable workingScope = masterScope.createWorkingScope();
		Object jsObject = context.evaluateString(workingScope, "[['3.0','British Aircraft Corp. Staff'],['8.0','British Telecom Engineer'],['10.0','British Gas Employee'],['10.0','British Nuclear Fuels Employee'],['10.0','British Rail Employee'],['10.0','British Road Services Employee'],['10.0','British Steel Employee'],['10.0','British Telecom Employee'],['15.0','Bricklayer'],['15.0','British Tourist Board Employee']]", "Create occupation array", 1, null);
		netUtilReturnObject = jsObject;
		
		Set<String> fieldsInError = formSubmissionHelper.validateInput(inputs);
		Assert.assertEquals(null, netUtilUrlRequested);
		Assert.assertEquals(1, fieldsInError.size());
		Assert.assertEquals("occupation", fieldsInError.iterator().next());
		
		occupation.setValue("Bricklayer");
		Assert.assertEquals(0, formSubmissionHelper.validateInput(inputs).size());
		Assert.assertEquals("http://somewhere/something?value=bri", netUtilUrlRequested);
	}
	
	@Test
	public void testValidationFunction() throws Exception {
		ArrayList<InputPojo> inputs = new ArrayList<InputPojo>();
		InputPojo marriedInputPojo = new InputPojo("married", "checkbox", new HashMap<String, String>());
		marriedInputPojo.setValue("true");
		inputs.add(marriedInputPojo);
		HashMap<String, String> rfAttributes = new HashMap<String, String>();
		rfAttributes.put(Constants.VALIDATION_FUNCTION_ATTR, "{ if(fields.married.value == true) { this.validate('required'); } }");
		InputPojo madenNameInputPojo = new InputPojo("madenName", "text", rfAttributes);
		inputs.add(madenNameInputPojo);
		
		Set<String> fieldsInError = formSubmissionHelper.validateInput(inputs);
		Assert.assertEquals(1, fieldsInError.size());
		Assert.assertEquals("madenName", fieldsInError.iterator().next());
		
		marriedInputPojo.setValue("false");
		Assert.assertEquals(0, formSubmissionHelper.validateInput(inputs).size());
	}
	
}
