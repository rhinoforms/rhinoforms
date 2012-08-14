package com.rhinoforms;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

import com.rhinoforms.resourceloader.ResourceLoader;

public class RhinoFormsScopeFactory {
	
	public ScriptableObject createMasterScope(Context jsContext, ResourceLoader resourceLoader) throws IOException {
		ScriptableObject scope = jsContext.initStandardObjects(null, true);
		String scriptPath = Constants.RHINOFORM_SCRIPT;
		InputStream resourceAsStream = resourceLoader.getResourceAsStream(scriptPath);
		jsContext.evaluateReader(scope, new InputStreamReader(resourceAsStream), scriptPath, 1, null);
		jsContext.evaluateString(scope, "rf = new Rhinoforms();", "Create rf instance", 1, null);
		//scope.sealObject(); // We can't seal the object because of a bug when xerces or xalan are on the classpath. 
		return scope;
	}
	
}
