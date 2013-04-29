package com.rhinoforms.js;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rhinoforms.Constants;
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
		
		loadScript(Constants.RHINOFORMS_SCRIPT, workingScope, jsContext, resourceLoader);
		
		// Define an instance of the Rhinoforms javascript object as 'rf' in the global namespace
		jsContext.evaluateString(workingScope, "var rf = new Rhinoforms();", "Create rf instance", 1, null);
		
		// Load server-side only functions into the scope
		loadScript(Constants.RHINOFORMS_SERVER_SIDE_SCRIPT, workingScope, jsContext, resourceLoader);
		
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

	private void loadScript(String scriptPath, Scriptable scope, Context jsContext, ResourceLoader resourceLoader)
			throws FileNotFoundException, IOException {
		InputStream resourceAsStream = resourceLoader.getWebappResourceAsStream(scriptPath);
		jsContext.evaluateReader(scope, new InputStreamReader(resourceAsStream), scriptPath, 1, null);
	}
	
}
