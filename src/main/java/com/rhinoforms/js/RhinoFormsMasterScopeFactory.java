package com.rhinoforms.js;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.LoggerFactory;

import com.rhinoforms.resourceloader.ResourceLoader;

public class RhinoFormsMasterScopeFactory {

	private static final String RHINOFORMS_SCRIPT = "/js/rhinoforms.js";
	private static final String RHINOFORMS_SERVER_SIDE_SCRIPT = "/js/rhinoforms-server-side.js";
	private static final String SERVER_SIDE_CONSOLE_SCRIPT = "/js/server-side-console.js";
	
	public JSMasterScope createMasterScope(Context jsContext, ResourceLoader resourceLoader) throws IOException {
		ScriptableObject sharedScope = jsContext.initStandardObjects(null, true);

		JSMasterScope masterScope = new JSMasterScope(sharedScope, resourceLoader);
		
		loadScript(RHINOFORMS_SCRIPT, sharedScope, jsContext, resourceLoader);
		
		// Define an instance of the Rhinoforms javascript object as 'rf' in the global namespace
		jsContext.evaluateString(sharedScope, "var rf = new Rhinoforms();", "Create rf instance", 1, null);
		
		// Load server-side only functions into the scope
		loadScript(RHINOFORMS_SERVER_SIDE_SCRIPT, sharedScope, jsContext, resourceLoader);

		// Define netUtil for use by server-side functions
		NetUtil netUtil = createNetUtil(masterScope);
		Object wrappedNetUtil = Context.javaToJS(netUtil, sharedScope);
		ScriptableObject.putProperty(sharedScope, "netUtil", wrappedNetUtil);
		
		// Enable logging
		ScriptableObject.putProperty(sharedScope, "logger", LoggerFactory.getLogger(JSMasterScope.class));
		loadScript(SERVER_SIDE_CONSOLE_SCRIPT, sharedScope, jsContext, resourceLoader);
		jsContext.evaluateString(sharedScope, "var console = new Console();", "Create console", 1, null);
		
		// Seal scope objects
		Object[] propertyIds = ScriptableObject.getPropertyIds(sharedScope);
		for (Object object : propertyIds) {
			Object property = ScriptableObject.getProperty(sharedScope, (String) object);
			if (property instanceof ScriptableObject) {
				((ScriptableObject)property).sealObject();
			}
		}
		
		// Seal the sharedScope to prevent further modification
		sharedScope.sealObject();

		return masterScope;
	}

	public static void enableDynamicScopeFeature() {
		if (!ContextFactory.hasExplicitGlobal()) {
			ContextFactory.initGlobal(new DynamicScopeEnabledContextFactory());
		}
	}
	
	NetUtil createNetUtil(JSMasterScope masterScope) {
		return new NetUtilImpl(masterScope);
	}

	private void loadScript(String scriptPath, Scriptable scope, Context jsContext, ResourceLoader resourceLoader)
			throws FileNotFoundException, IOException {
		InputStream resourceAsStream = resourceLoader.getWebappResourceAsStream(scriptPath);
		jsContext.evaluateReader(scope, new InputStreamReader(resourceAsStream), scriptPath, 1, null);
	}
	
	private static final class DynamicScopeEnabledContextFactory extends ContextFactory {
		
		@Override
		protected boolean hasFeature(Context cx, int featureIndex) {
			if (featureIndex == Context.FEATURE_DYNAMIC_SCOPE) {
				return true;
			} else {
				return super.hasFeature(cx, featureIndex);
			}
		}
		
	}
	
}
