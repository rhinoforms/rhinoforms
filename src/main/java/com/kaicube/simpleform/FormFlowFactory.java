package com.kaicube.simpleform;

import org.mozilla.javascript.Context;

public class FormFlowFactory {

	private Context jsContext;

	public FormFlowFactory() {
		this.jsContext = Context.enter();
	}

	public FormFlow createFlow() {
		return new FormFlow(jsContext);
	}

}
