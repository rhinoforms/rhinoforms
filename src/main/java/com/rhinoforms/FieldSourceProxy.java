package com.rhinoforms;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rhinoforms.util.StreamUtils;
import com.rhinoforms.util.URIUtil;

public class FieldSourceProxy {

	private String urlString;
	private String proxyPath;

	private static StreamUtils streamUtils = new StreamUtils();
	private static URIUtil uriUtil = new URIUtil();

	final Logger logger = LoggerFactory.getLogger(FieldSourceProxy.class);

	public FieldSourceProxy(String proxyPath, String url) {
		this.proxyPath = proxyPath;
		this.urlString = url;
	}

	public String getUrl() {
		return urlString;
	}

	public String getProxyPath() {
		return proxyPath;
	}

	public void makeRequest(Map<String, String> parameterMap, HttpServletResponse response) throws FieldSourceProxyException {
		String thisUrl = urlString;

		// If present inject input value into URL
		String value = parameterMap.get("value");
		if (value != null) {
			thisUrl = thisUrl.replace("{value}", value);
			parameterMap.remove("value");
		}

		// Add params from this request.
		String paramsFromProxyCall = uriUtil.paramsMapToString(parameterMap);
		if (!paramsFromProxyCall.isEmpty()) {
			if (!thisUrl.contains("?")) {
				thisUrl += "?";
			} else {
				thisUrl += "&";
			}
			thisUrl += paramsFromProxyCall;
		}

		logger.debug("Proxying url {}", thisUrl);

		connectViaUrlConnection(thisUrl, response);
	}

	private void connectViaUrlConnection(String thisUrl, HttpServletResponse response) throws FieldSourceProxyException {
		HttpURLConnection connection = null;
		InputStream inputStream = null;
		try {
			connection = (HttpURLConnection) new URL(thisUrl).openConnection();
			int responseCode = connection.getResponseCode();
			if (responseCode == 200) {
				response.setContentLength(connection.getContentLength());
				response.setContentType(connection.getContentType());
				inputStream = connection.getInputStream();
				streamUtils.copyInputStreamToOutputStream(inputStream, response.getOutputStream());
			} else {
				response.sendError(responseCode, connection.getResponseMessage());
			}
		} catch (IOException e) {
			logger.error("Proxy URL problem '{}'", thisUrl, e);
			throw new FieldSourceProxyException();
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
					logger.error("Failed to close connection to proxy url.", e);
				}
			}
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

}
