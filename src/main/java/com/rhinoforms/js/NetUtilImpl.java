package com.rhinoforms.js;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetUtilImpl implements NetUtil {

	final Logger logger = LoggerFactory.getLogger(NetUtilImpl.class);
	
	public Object httpGetJsObject(String urlString) throws IOException {
		logger.debug("httpGetJsObject for url:'{}'", urlString);
		Context context = Context.enter();
		ScriptableObject scope = context.initStandardObjects();
		try {
			URL url = new URL(urlString);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream());
			return context.evaluateReader(scope, inputStreamReader, "", 1, null);
		} finally {
			Context.exit();
		}
	}
	
}
