package com.rhinoforms.js;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rhinoforms.resourceloader.ResourceLoader;

public class JSMasterScope {

	private Scriptable sharedScope;
	private ResourceLoader resourceLoader;
	private final Logger logger = LoggerFactory.getLogger(JSMasterScope.class);
	
	public JSMasterScope(Scriptable sharedScope, ResourceLoader resourceLoader) {
		this.sharedScope = sharedScope;
		this.resourceLoader = resourceLoader;
	}

	/**
	 * Creates a new scope with the master as it's prototype.
	 * 
	 * @return
	 * @throws IOException 
	 */
	public Scriptable createWorkingScope() throws IOException {
		Context jsContext = getCurrentContext();
		Scriptable workingScope = jsContext.newObject(sharedScope);
		workingScope.setPrototype(sharedScope);
		workingScope.setParentScope(null);
		return workingScope;
	}

	public Scriptable createWorkingScope(List<String> librariesToPreload) throws IOException, FlowExceptionFileNotFound {
		Scriptable workingScope = createWorkingScope();

		if (librariesToPreload != null && !librariesToPreload.isEmpty()) {
			Context jsContext = getCurrentContext();
			for (String libraryToPreload : librariesToPreload) {
				InputStream resourceAsStream = resourceLoader.getFormResourceAsStream(libraryToPreload);
				if (resourceAsStream != null) {
					logger.debug("Loading library script {}", libraryToPreload);
					jsContext.evaluateReader(workingScope, new InputStreamReader(resourceAsStream), libraryToPreload, 1, null);
				} else {
					throw new FlowExceptionFileNotFound("Could not locate flow library '" + libraryToPreload + "'");
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
