package com.rhinoforms;

import java.util.List;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import com.rhinoforms.serverside.InputPojo;

public class FormFlow {

	private int id;
	private Scriptable scope;
	private List<InputPojo> currentInputPojos;
	
	public FormFlow(Context jsContext) {
		this.id = (int) (Math.random() * 100000000f);
		this.scope = jsContext.initStandardObjects();
	}
	
	public int getId() {
		return id;
	}
	
	public Scriptable getScope() {
		return scope;
	}

	public List<InputPojo> getCurrentInputPojos() {
		return currentInputPojos;
	}
	
	public void setCurrentInputPojos(List<InputPojo> currentInputPojos) {
		this.currentInputPojos = currentInputPojos;
	}
	
}
