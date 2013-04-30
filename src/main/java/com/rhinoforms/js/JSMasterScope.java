package com.rhinoforms.js;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rhinoforms.resourceloader.ResourceLoader;

public class JSMasterScope {

	private Scriptable sharedScope;
	private ResourceLoader resourceLoader;
	private final Logger logger = LoggerFactory.getLogger(JSMasterScope.class);
	
	private static final String RHINOFORMS_SCRIPT = "/js/rhinoforms.js";
	private static final String RHINOFORMS_SERVER_SIDE_SCRIPT = "/js/rhinoforms-server-side.js";
	private static final String SERVER_SIDE_CONSOLE_SCRIPT = "/js/server-side-console.js";


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
		
		loadScript(RHINOFORMS_SCRIPT, workingScope, jsContext, resourceLoader);
		
		// Define an instance of the Rhinoforms javascript object as 'rf' in the global namespace
		jsContext.evaluateString(workingScope, "var rf = new Rhinoforms();", "Create rf instance", 1, null);
		
		// Load server-side only functions into the scope
		loadScript(RHINOFORMS_SERVER_SIDE_SCRIPT, workingScope, jsContext, resourceLoader);
		
		// Enable logging
		ScriptableObject.putProperty(workingScope, "logger", LoggerFactory.getLogger(JSMasterScope.class));
		loadScript(SERVER_SIDE_CONSOLE_SCRIPT, workingScope, jsContext, resourceLoader);
		jsContext.evaluateString(workingScope, "var console = new Console();", "Create console", 1, null);
		
		return workingScope;
	}

	public Scriptable createWorkingScope(List<String> librariesToPreload) throws IOException {
		Scriptable workingScope = createWorkingScope();

		if (librariesToPreload != null && !librariesToPreload.isEmpty()) {
			Context jsContext = getCurrentContext();
			for (String libraryToPreload : librariesToPreload) {
				InputStream resourceAsStream = resourceLoader.getFormResourceAsStream(libraryToPreload);
				if (resourceAsStream != null) {
					logger.debug("Loading library script {}", libraryToPreload);
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

	private void loadScript(String scriptPath, Scriptable scope, Context jsContext, ResourceLoader resourceLoader)
			throws FileNotFoundException, IOException {
		InputStream resourceAsStream = resourceLoader.getWebappResourceAsStream(scriptPath);
		jsContext.evaluateReader(scope, new InputStreamReader(resourceAsStream), scriptPath, 1, null);
	}
	
}
