package com.rhinoforms.serverside;

import java.util.Map;

import com.rhinoforms.Constants;

public class InputPojo {

	private String name;
	private String type;
	private String value;
	private Map<String, String> rfAttributes;

	public InputPojo(String name, String type, Map<String, String> rfAttributes) {
		this.name = name;
		this.type = type;
		this.rfAttributes = rfAttributes;
		value = "";
	}

	public InputPojo(String name, String value) {
		this.name = name;
		this.value = value;
	}

	public String getValidation() {
		return rfAttributes.get(Constants.VALIDATION_ATTR);
	}

	public String getValidationFunction() {
		return rfAttributes.get(Constants.VALIDATION_FUNCTION_ATTR);
	}

	// Standard getters and setters
	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
	
	public Map<String, String> getRfAttributes() {
		return rfAttributes;
	}

}
