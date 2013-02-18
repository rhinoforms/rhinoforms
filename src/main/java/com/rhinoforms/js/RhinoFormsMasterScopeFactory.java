package com.rhinoforms.js;

import java.io.IOException;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

import com.rhinoforms.resourceloader.ResourceLoader;

public class RhinoFormsMasterScopeFactory {

	public JSMasterScope createMasterScope(Context jsContext, ResourceLoader resourceLoader) throws IOException {
		ScriptableObject sharedScope = jsContext.initStandardObjects(null, true);

		JSMasterScope masterScope = new JSMasterScope(sharedScope, resourceLoader);
		
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

}
