package com.rhinoforms;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import com.rhinoforms.resourceloader.ResourceLoader;

public class JSMasterScope {

	private Scriptable sharedScope;
	private ResourceLoader resourceLoader;

	public JSMasterScope(Scriptable sharedScope, ResourceLoader resourceLoader) {
		this.sharedScope = sharedScope;
		this.resourceLoader = resourceLoader;
	}

	/**
	 * Creates a new scope with the master as it's prototype.
	 * 
	 * @return
	 */
	public Scriptable createWorkingScope() {
		Scriptable workingScope = getCurrentContext().newObject(sharedScope);
		workingScope.setPrototype(sharedScope);
		workingScope.setParentScope(null);
		return workingScope;
	}

	public Scriptable createWorkingScope(List<String> librariesToPreload) throws IOException {
		Scriptable workingScope = createWorkingScope();

		if (librariesToPreload != null && !librariesToPreload.isEmpty()) {
			Context jsContext = getCurrentContext();
			for (String libraryToPreload : librariesToPreload) {
				InputStream resourceAsStream = resourceLoader.getResourceAsStream(libraryToPreload);
				if (resourceAsStream != null) {
					jsContext.evaluateReader(workingScope, new InputStreamReader(resourceAsStream), libraryToPreload, 1, null);
				} else {
					throw new IOException("Could not locate flow library '" + libraryToPreload + "'");
				}
			}
		}

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
