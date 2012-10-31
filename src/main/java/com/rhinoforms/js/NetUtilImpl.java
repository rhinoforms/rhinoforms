package com.rhinoforms.js;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NetUtilImpl implements NetUtil {

	private JSMasterScope masterScope;
	final Logger logger = LoggerFactory.getLogger(NetUtilImpl.class);
	
	public NetUtilImpl(JSMasterScope masterScope) {
		this.masterScope = masterScope;
	}
	
	public Object httpGetJsObject(String urlString) throws IOException {
		logger.debug("httpGetJsObject for url:'{}'", urlString);
		Context context = masterScope.getCurrentContext();
		Scriptable workingScope = masterScope.createWorkingScope();
		URL url = new URL(urlString);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream());
		return context.evaluateReader(workingScope, inputStreamReader, "", 1, null);
	}
	
}
