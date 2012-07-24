package com.rhinoforms.serverside;

public class InputPojo {

	public String name;
	public String type;
	public String validation;
	public String validationFunction;
	public String value;

	public InputPojo(String name, String type, String validation, String validationFunction) {
		this.name = name;
		this.type = type;
		this.validation = validation;
		this.validationFunction = validationFunction;
	}

}
