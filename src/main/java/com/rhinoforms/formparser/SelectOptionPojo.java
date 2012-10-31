package com.rhinoforms.formparser;

public class SelectOptionPojo {

	private String text;
	private String value;

	public SelectOptionPojo(String text) {
		this.text = text;
	}
	
	public SelectOptionPojo(String text, String value) {
		this.text = text;
		this.value = value;
	}
	
	public String getText() {
		return text;
	}

	public String getValue() {
		return value;
	}
	
}
