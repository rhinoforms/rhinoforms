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
	 * @return
	 */
	public Scriptable createWorkingScope() {
			Scriptable workingScope = getCurrentContext().newObject(sharedScope);
			workingScope.setPrototype(sharedScope);
			workingScope.setParentScope(null);
			return workingScope;
	}
	
	public Context getCurrentContext() {
		Context currentContext = Context.getCurrentContext();
		if (currentContext != null) {
			return currentContext;
		} else {
			throw new RuntimeException("There is no js Context bound to the current thread.");
		}
	}
	
}
