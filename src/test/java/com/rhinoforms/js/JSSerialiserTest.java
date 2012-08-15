package com.rhinoforms.js;

import java.util.ArrayList;
import java.util.HashMap;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import com.rhinoforms.Constants;
import com.rhinoforms.serverside.InputPojo;

public class JSSerialiserTest {

	private JSSerialiser jsSerialiser;

	@Before
	public void before() {
		this.jsSerialiser = new JSSerialiser();
	}
	
	@Test
	public void testNoAtt() throws Exception {
		ArrayList<InputPojo> inputPojos = new ArrayList<InputPojo>();
		inputPojos.add(new InputPojo("one", "text", new HashMap<String, String>()));
		String js = jsSerialiser.inputPOJOListToJS(inputPojos);
//		System.out.println(js.replaceAll("\"", "\\\\\""));
		Assert.assertEquals("{\"one\":{name:\"one\",value:\"null\",rfAttributes:{}}}", js);
	}
	
	@Test
	public void testValAtt() throws Exception {
		ArrayList<InputPojo> inputPojos = new ArrayList<InputPojo>();
		HashMap<String, String> rfAttributes = new HashMap<String, String>();
		rfAttributes.put(Constants.VALIDATION_ATTR, "required");
		inputPojos.add(new InputPojo("one", "text", rfAttributes));
		String js = jsSerialiser.inputPOJOListToJS(inputPojos);
//		System.out.println(js.replaceAll("\"", "\\\\\""));
		Assert.assertEquals("{\"one\":{name:\"one\",value:\"null\",validation:\"required\",rfAttributes:{}}}", js);
	}
	
	@Test
	public void testValFuncAtt() throws Exception {
		ArrayList<InputPojo> inputPojos = new ArrayList<InputPojo>();
		HashMap<String, String> rfAttributes = new HashMap<String, String>();
		rfAttributes.put(Constants.VALIDATION_FUNCTION_ATTR, "{ if(fields.veggie.value == false) { this.validate(\"required\"); } }");
		inputPojos.add(new InputPojo("one", "text", rfAttributes));
		String js = jsSerialiser.inputPOJOListToJS(inputPojos);
//		System.out.println(js.replaceAll("\"", "\\\\\""));
		Assert.assertEquals("{\"one\":{name:\"one\",value:\"null\",validationFunction:\"{ if(fields.veggie.value == false) { this.validate('required'); } }\",rfAttributes:{}}}", js);
	}
	
	@Test
	public void testSourceAtt() throws Exception {
		ArrayList<InputPojo> inputPojos = new ArrayList<InputPojo>();
		HashMap<String, String> rfAttributes = new HashMap<String, String>();
		rfAttributes.put(Constants.SELECT_SOURCE_ATTR, "http://somewhere/something?occupation=bricklayer");
		inputPojos.add(new InputPojo("one", "text", rfAttributes));
		String js = jsSerialiser.inputPOJOListToJS(inputPojos);
//		System.out.println(js.replaceAll("\"", "\\\\\""));
		Assert.assertEquals("{\"one\":{name:\"one\",value:\"null\",rfAttributes:{\"rf.source\":\"http://somewhere/something?occupation=bricklayer\"}}}", js);
	}
	
	@Test
	public void testAllAtt() throws Exception {
		ArrayList<InputPojo> inputPojos = new ArrayList<InputPojo>();
		HashMap<String, String> rfAttributes = new HashMap<String, String>();
		rfAttributes.put(Constants.VALIDATION_ATTR, "required");
		rfAttributes.put(Constants.VALIDATION_FUNCTION_ATTR, "{ if(fields.veggie.value == false) { this.validate(\"required\"); } }");
		rfAttributes.put(Constants.SELECT_SOURCE_ATTR, "http://somewhere/something?occupation=bricklayer");
		rfAttributes.put("rf.customType", "auto-complete-select");
		inputPojos.add(new InputPojo("one", "text", rfAttributes));
		String js = jsSerialiser.inputPOJOListToJS(inputPojos);
//		System.out.println(js.replaceAll("\"", "\\\\\""));
		Assert.assertEquals("{\"one\":{name:\"one\",value:\"null\",validation:\"required\",validationFunction:\"{ if(fields.veggie.value == false) { this.validate('required'); } }\",rfAttributes:{\"rf.customType\":\"auto-complete-select\",\"rf.source\":\"http://somewhere/something?occupation=bricklayer\"}}}", js);
	}
	
}
