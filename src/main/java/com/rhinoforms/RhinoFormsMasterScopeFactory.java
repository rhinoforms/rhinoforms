package com.rhinoforms;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

import com.rhinoforms.js.NetUtil;
import com.rhinoforms.js.NetUtilImpl;
import com.rhinoforms.resourceloader.ResourceLoader;

public class RhinoFormsMasterScopeFactory {

	public JSMasterScope createMasterScope(Context jsContext, ResourceLoader resourceLoader) throws IOException {
		ScriptableObject sharedScope = jsContext.initStandardObjects(null, false);

		// Load main javascript library into shared scope
		loadScript(Constants.RHINOFORMS_SCRIPT, sharedScope, jsContext, resourceLoader);
		
		// Define an instance of the Rhinoforms javascript object as 'rf' in the global namespace
		jsContext.evaluateString(sharedScope, "rf = new Rhinoforms();", "Create rf instance", 1, null);
		
		// Load server-side only functions into the scope
		loadScript(Constants.RHINOFORMS_SERVER_SIDE_SCRIPT, sharedScope, jsContext, resourceLoader);
		
		JSMasterScope masterScope = new JSMasterScope(sharedScope);
		
		// Define netUtil for use by server-side functions
		NetUtil netUtil = createNetUtil(masterScope);
		Object wrappedNetUtil = Context.javaToJS(netUtil, sharedScope);
		ScriptableObject.putProperty(sharedScope, "netUtil", wrappedNetUtil);

		// It would be good to seal the sharedScope here but we can't because of a bug when xerces or xalan is on the classpath.
//		sharedScope.sealObject();

		return masterScope;
	}

	NetUtil createNetUtil(JSMasterScope masterScope) {
		return new NetUtilImpl(masterScope);
	}

	private void loadScript(String scriptPath, ScriptableObject sharedScope, Context jsContext, ResourceLoader resourceLoader)
			throws FileNotFoundException, IOException {
		InputStream resourceAsStream = resourceLoader.getResourceAsStream(scriptPath);
		jsContext.evaluateReader(sharedScope, new InputStreamReader(resourceAsStream), scriptPath, 1, null);
	}
	
}
