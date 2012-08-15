package com.rhinoforms;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

import com.rhinoforms.resourceloader.ResourceLoader;

public class RhinoFormsMasterScopeFactory {
	
	public JSMasterScope createMasterScope(Context jsContext, ResourceLoader resourceLoader) throws IOException {
		ScriptableObject sharedScope = jsContext.initStandardObjects(null, true);
		String scriptPath = Constants.RHINOFORM_SCRIPT;
		InputStream resourceAsStream = resourceLoader.getResourceAsStream(scriptPath);
		jsContext.evaluateReader(sharedScope, new InputStreamReader(resourceAsStream), scriptPath, 1, null);
		jsContext.evaluateString(sharedScope, "rf = new Rhinoforms();", "Create rf instance", 1, null);
		//sharedScope.sealObject(); // We can't seal the object because of a bug when xerces or xalan are on the classpath.
		return new JSMasterScope(sharedScope);
	}
	
}
