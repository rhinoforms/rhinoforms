package com.rhinoforms.flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import com.rhinoforms.Constants;
import com.rhinoforms.TestNetUtil;
import com.rhinoforms.TestResourceLoader;
import com.rhinoforms.js.JSMasterScope;
import com.rhinoforms.js.RhinoFormsMasterScopeFactory;
import com.rhinoforms.js.TestRhinoFormsMasterScopeFactory;
import com.rhinoforms.resourceloader.ResourceLoaderImpl;

public class FormSubmissionHelperTest {

	private Context context;
	private JSMasterScope masterScope;
	private FormSubmissionHelper formSubmissionHelper;
	private TestNetUtil testNetUtil;
	private Scriptable workingScope;
	private String actionName;
	
	@Before
	public void before() throws Exception {
		RhinoFormsMasterScopeFactory.enableDynamicScopeFeature();
		context = Context.enter();
		testNetUtil = new TestNetUtil();
		
		// Modified factory which uses testNetUtil
		RhinoFormsMasterScopeFactory masterScopeFactory = new TestRhinoFormsMasterScopeFactory(testNetUtil);
		
		ResourceLoaderImpl resourceLoader = new ResourceLoaderImpl(new TestResourceLoader(), new TestResourceLoader());
		masterScope = masterScopeFactory.createMasterScope(context, resourceLoader);
		formSubmissionHelper = new FormSubmissionHelper(masterScope);
		workingScope = masterScope.createWorkingScope();
		actionName = "next";
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
		
		Set<String> fieldsInError = formSubmissionHelper.validateInput(inputs, actionName, workingScope);
		Assert.assertEquals(1, fieldsInError.size());
		Assert.assertEquals("name", fieldsInError.iterator().next());
		
		married.setValue("Kai");
		Assert.assertEquals(0, formSubmissionHelper.validateInput(inputs, actionName, workingScope).size());
	}
	
	@Test
	public void testValidationWithSpecialCharacter() throws Exception {
		ArrayList<InputPojo> inputs = new ArrayList<InputPojo>();
		HashMap<String, String> rfAttributes = new HashMap<String, String>();
		rfAttributes.put(Constants.VALIDATION_ATTR, "required");
		InputPojo married = new InputPojo("name", "text", rfAttributes);
		married.setValue("\"");
		inputs.add(married);
		
		Set<String> fieldsInError = formSubmissionHelper.validateInput(inputs, actionName, workingScope);
		Assert.assertEquals(0, fieldsInError.size());
	}
	
	@Test
	public void testValidationKeyword_date() throws Exception {
		ArrayList<InputPojo> inputs = new ArrayList<InputPojo>();
		HashMap<String, String> rfAttributes = new HashMap<String, String>();
		rfAttributes.put(Constants.VALIDATION_ATTR, "date");
		InputPojo dob = new InputPojo("dob", "text", rfAttributes);
		dob.setValue("1");
		inputs.add(dob);
		
		Set<String> fieldsInError = formSubmissionHelper.validateInput(inputs, actionName, workingScope);
		Assert.assertEquals(1, fieldsInError.size());
		Assert.assertEquals("dob", fieldsInError.iterator().next());
		
		dob.setValue("20/12/1983");
		Assert.assertEquals(0, formSubmissionHelper.validateInput(inputs, actionName, workingScope).size());
		Assert.assertEquals(0, formSubmissionHelper.validateInput(inputs, actionName, workingScope).size());
		Assert.assertEquals(0, formSubmissionHelper.validateInput(inputs, actionName, workingScope).size());
	}
	
	@Test
	public void testValidationKeyword_fromSource() throws Exception {
		
		ArrayList<InputPojo> inputs = new ArrayList<InputPojo>();
		HashMap<String, String> rfAttributes = new HashMap<String, String>();
		rfAttributes.put(Constants.SELECT_SOURCE_ATTR, "http://somewhere/something?value=[value]");
		rfAttributes.put(Constants.VALIDATION_ATTR, "fromSource");
		InputPojo occupation = new InputPojo("occupation", "text", rfAttributes);
		inputs.add(occupation);
		
		Scriptable workingScope = masterScope.createWorkingScope();
		Object jsObject = context.evaluateString(workingScope, "[['3.0','British Aircraft Corp. Staff'],['8.0','British Telecom Engineer'],['10.0','British Gas Employee'],['10.0','British Nuclear Fuels Employee'],['10.0','British Rail Employee'],['10.0','British Road Services Employee'],['10.0','British Steel Employee'],['10.0','British Telecom Employee'],['15.0','Bricklayer'],['15.0','British Tourist Board Employee']]", "Create occupation array", 1, null);
		testNetUtil.setReturnObject(jsObject);
		
		Set<String> fieldsInError = formSubmissionHelper.validateInput(inputs, actionName, workingScope);
		Assert.assertEquals(null, testNetUtil.getUrlRequested());
		Assert.assertEquals(1, fieldsInError.size());
		Assert.assertEquals("occupation", fieldsInError.iterator().next());
		
		occupation.setValue("Bricklayer");
		Assert.assertEquals(0, formSubmissionHelper.validateInput(inputs, actionName, workingScope).size());
		Assert.assertEquals("http://somewhere/something?value=bri", testNetUtil.getUrlRequested());
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
		
		Set<String> fieldsInError = formSubmissionHelper.validateInput(inputs, actionName, workingScope);
		Assert.assertEquals(1, fieldsInError.size());
		Assert.assertEquals("madenName", fieldsInError.iterator().next());
		
		marriedInputPojo.setValue("false");
		Assert.assertEquals(0, formSubmissionHelper.validateInput(inputs, actionName, workingScope).size());
	}
	
	@Test
	public void testTextareaValidation() throws Exception {
		ArrayList<InputPojo> inputs = new ArrayList<InputPojo>();
		InputPojo marriedInputPojo = new InputPojo("text", "textarea", new HashMap<String, String>());
		marriedInputPojo.setValue("first line\nsecond line");
		inputs.add(marriedInputPojo);
		
		Set<String> fieldsInError = formSubmissionHelper.validateInput(inputs, actionName, workingScope);
		Assert.assertEquals(0, fieldsInError.size());
	}
	
	@Test
	public void testValidationFunctionUsingActionCondition() throws Exception {
		ArrayList<InputPojo> inputs = new ArrayList<InputPojo>();
		InputPojo marriedInputPojo = new InputPojo("married", "checkbox", new HashMap<String, String>());
		marriedInputPojo.setValue("true");
		inputs.add(marriedInputPojo);
		HashMap<String, String> rfAttributes = new HashMap<String, String>();
		rfAttributes.put(Constants.VALIDATION_FUNCTION_ATTR, "{ if(action == 'next' && fields.married.value == true) { this.validate('required'); } }");
		InputPojo madenNameInputPojo = new InputPojo("madenName", "text", rfAttributes);
		inputs.add(madenNameInputPojo);
		
		Set<String> fieldsInError = formSubmissionHelper.validateInput(inputs, "next", workingScope);
		Assert.assertEquals(1, fieldsInError.size());
		Assert.assertEquals("madenName", fieldsInError.iterator().next());
		
		Set<String> fieldsInErrorForSkip = formSubmissionHelper.validateInput(inputs, "skip", workingScope);
		Assert.assertEquals(0, fieldsInErrorForSkip.size());
		
		marriedInputPojo.setValue("false");
		Assert.assertEquals(0, formSubmissionHelper.validateInput(inputs, "next", workingScope).size());
		Assert.assertEquals(0, formSubmissionHelper.validateInput(inputs, "skip", workingScope).size());
	}
	
	@Test
	public void testValidationFunctionUsingFlowLibrary() throws Exception {
		ArrayList<String> librariesToPreload = new ArrayList<String>();
		librariesToPreload.add("com/rhinoforms/flow/flow-library-example.js");
		Scriptable scope = masterScope.createWorkingScope(librariesToPreload);
		
		ArrayList<InputPojo> inputs = new ArrayList<InputPojo>();
		InputPojo marriedInputPojo = new InputPojo("married", "checkbox", new HashMap<String, String>());
		marriedInputPojo.setValue("true");
		inputs.add(marriedInputPojo);
		HashMap<String, String> rfAttributes = new HashMap<String, String>();
		rfAttributes.put(Constants.VALIDATION_FUNCTION_ATTR, "{ if( isTrue(fields.married.value) ) { this.validate('required'); } }");
		InputPojo madenNameInputPojo = new InputPojo("madenName", "text", rfAttributes);
		inputs.add(madenNameInputPojo);
		
		Set<String> fieldsInError = formSubmissionHelper.validateInput(inputs, actionName, scope);
		Assert.assertEquals(1, fieldsInError.size());
		Assert.assertEquals("madenName", fieldsInError.iterator().next());
		
		marriedInputPojo.setValue("false");
		Assert.assertEquals(0, formSubmissionHelper.validateInput(inputs, actionName, scope).size());
	}
	
	@Test
	public void testGetIncludeFalseInputs() throws Exception {
		ArrayList<InputPojo> inputs = new ArrayList<InputPojo>();
		
		InputPojo movedHouseInput = new InputPojo("movedHouse", "checkbox", new HashMap<String, String>());
		movedHouseInput.setValue("true");
		inputs.add(movedHouseInput);
		
		HashMap<String, String> oldAddressRfAttributes = new HashMap<String, String>();
		oldAddressRfAttributes.put(Constants.INCLUDE_IF_ATTR, "{ fields.movedHouse.value == true }");
		InputPojo oldAddressInput = new InputPojo("oldAddress", "text", oldAddressRfAttributes);
		inputs.add(oldAddressInput);
		
		Assert.assertEquals(0, formSubmissionHelper.getIncludeFalseInputs(inputs, workingScope).size());
		
		movedHouseInput.setValue("false");
		
		List<InputPojo> includeFalseInputs = formSubmissionHelper.getIncludeFalseInputs(inputs, workingScope);
		Assert.assertEquals(1, includeFalseInputs.size());
		Assert.assertEquals("oldAddress", includeFalseInputs.get(0).getName());
	}
	
	@Test
	public void testProcessCalculatedFields() {
		ArrayList<InputPojo> inputs = new ArrayList<InputPojo>();
		
		InputPojo basePremium = new InputPojo("basePremium", "text", new HashMap<String, String>());
		basePremium.setValue("100");
		inputs.add(basePremium);

		InputPojo voluntaryExcess = new InputPojo("voluntaryExcess", "text", new HashMap<String, String>());
		inputs.add(voluntaryExcess);
		
		HashMap<String, String> premiumAttributes = new HashMap<String, String>();
		premiumAttributes.put(Constants.CALCULATED_ATTR, "{ new Number(fields.basePremium.value) + ( 30 - (fields.voluntaryExcess.value * 3) ) }");
		InputPojo premium = new InputPojo("premium", "text", premiumAttributes);
		inputs.add(premium);
		
		voluntaryExcess.setValue("0");
		formSubmissionHelper.processCalculatedFields(inputs, workingScope);
		Assert.assertEquals("130", premium.getValue());
		
		voluntaryExcess.setValue("5");
		formSubmissionHelper.processCalculatedFields(inputs, workingScope);
		Assert.assertEquals("115", premium.getValue());

		voluntaryExcess.setValue("10");
		formSubmissionHelper.processCalculatedFields(inputs, workingScope);
		Assert.assertEquals("100", premium.getValue());
	}

}
