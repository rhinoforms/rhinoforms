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
	public void testNameWithDot() throws Exception {
		ArrayList<InputPojo> inputPojos = new ArrayList<InputPojo>();
		inputPojos.add(new InputPojo("parent.one", "text", new HashMap<String, String>()));
		String js = jsSerialiser.inputPOJOListToJS(inputPojos);
		Assert.assertEquals("{\"parent_one\":{name:\"parent.one\",value:\"\",rfAttributes:{},included:true}}", js);
	}
	
	@Test
	public void testNoName() throws Exception {
		ArrayList<InputPojo> inputPojos = new ArrayList<InputPojo>();
		inputPojos.add(new InputPojo("", "text", new HashMap<String, String>()));
		String js = jsSerialiser.inputPOJOListToJS(inputPojos);
		Assert.assertEquals("{}", js);
	}
	
	@Test
	public void testNoAtt() throws Exception {
		ArrayList<InputPojo> inputPojos = new ArrayList<InputPojo>();
		inputPojos.add(new InputPojo("one", "text", new HashMap<String, String>()));
		String js = jsSerialiser.inputPOJOListToJS(inputPojos);
//		System.out.println(js.replaceAll("\"", "\\\\\""));
		Assert.assertEquals("{\"one\":{name:\"one\",value:\"\",rfAttributes:{},included:true}}", js);
	}
	
	@Test
	public void testValAtt() throws Exception {
		ArrayList<InputPojo> inputPojos = new ArrayList<InputPojo>();
		HashMap<String, String> rfAttributes = new HashMap<String, String>();
		rfAttributes.put(Constants.VALIDATION_ATTR, "required");
		inputPojos.add(new InputPojo("one", "text", rfAttributes));
		String js = jsSerialiser.inputPOJOListToJS(inputPojos);
//		System.out.println(js.replaceAll("\"", "\\\\\""));
		Assert.assertEquals("{\"one\":{name:\"one\",value:\"\",validation:\"required\",rfAttributes:{},included:true}}", js);
	}
	
	@Test
	public void testValFuncAtt() throws Exception {
		ArrayList<InputPojo> inputPojos = new ArrayList<InputPojo>();
		HashMap<String, String> rfAttributes = new HashMap<String, String>();
		rfAttributes.put(Constants.VALIDATION_FUNCTION_ATTR, "{ if(fields.veggie.value == false) { this.validate(\"required\"); } }");
		inputPojos.add(new InputPojo("one", "text", rfAttributes));
		String js = jsSerialiser.inputPOJOListToJS(inputPojos);
//		System.out.println(js.replaceAll("\"", "\\\\\""));
		Assert.assertEquals("{\"one\":{name:\"one\",value:\"\",validationFunction:\"{ if(fields.veggie.value == false) { this.validate('required'); } }\",rfAttributes:{},included:true}}", js);
	}
	
	@Test
	public void testSourceAtt() throws Exception {
		ArrayList<InputPojo> inputPojos = new ArrayList<InputPojo>();
		HashMap<String, String> rfAttributes = new HashMap<String, String>();
		rfAttributes.put(Constants.SELECT_SOURCE_ATTR, "http://somewhere/something?occupation=bricklayer");
		inputPojos.add(new InputPojo("one", "text", rfAttributes));
		String js = jsSerialiser.inputPOJOListToJS(inputPojos);
//		System.out.println(js.replaceAll("\"", "\\\\\""));
		Assert.assertEquals("{\"one\":{name:\"one\",value:\"\",rfAttributes:{\"rf.source\":\"http://somewhere/something?occupation=bricklayer\"},included:true}}", js);
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
		Assert.assertEquals("{\"one\":{name:\"one\",value:\"\",validation:\"required\",validationFunction:\"{ if(fields.veggie.value == false) { this.validate('required'); } }\",rfAttributes:{\"rf.customType\":\"auto-complete-select\",\"rf.source\":\"http://somewhere/something?occupation=bricklayer\"},included:true}}", js);
	}
	
}
