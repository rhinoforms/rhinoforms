package com.rhinoforms;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

public class JSMasterScope {

	private Scriptable sharedScope;

	public JSMasterScope(Scriptable sharedScope) {
		this.sharedScope = sharedScope;
	}
	
	/**
	 * Creates a new scope with the master as it's prototype.
	 * Warning - this method uses the org.mozilla.javascript.Context bound to the current thread but does not close it.
	 * @return
	 */
	public Scriptable createWorkingScope() {
		Scriptable workingScope = Context.enter().newObject(sharedScope);
		workingScope.setPrototype(sharedScope);
		workingScope.setParentScope(null);
		return workingScope;
	}
	
}
