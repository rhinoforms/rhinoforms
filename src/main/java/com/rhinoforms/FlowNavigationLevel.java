package com.rhinoforms;

import java.io.Serializable;
import java.util.List;

public class FlowNavigationLevel implements Serializable {

	private List<Form> currentFormList;
	private Form currentForm;
	private String docBase;
	private static final long serialVersionUID = -8322834501530178520L;

	public FlowNavigationLevel(List<Form> currentFormList, Form currentForm) {
		this.currentFormList = currentFormList;
		this.currentForm = currentForm;
	}
	
	public List<Form> getCurrentFormList() {
		return currentFormList;
	}
	
	public Form getCurrentForm() {
		return currentForm;
	}
	
	public void setCurrentForm(Form currentForm) {
		this.currentForm = currentForm;
	}

	public String getDocBase() {
		return docBase;
	}
	
	public void setDocBase(String docBase) {
		this.docBase = docBase;
	}

}
