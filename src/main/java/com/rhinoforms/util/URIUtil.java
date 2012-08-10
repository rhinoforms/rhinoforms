package com.rhinoforms.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

public class URIUtil {

	private static final Logger LOGGER = Logger.getLogger(URIUtil.class);
	private static final String UTF8 = "UTF-8";
	
	public Map<String, String> paramsStringToMap(String params) {
		Map<String, String> paramsMap = new HashMap<String, String>();
		if (params != null) {
			StringTokenizer st = new StringTokenizer(params, "&");
			while (st.hasMoreTokens()) {
				String token = st.nextToken();
				if (token.indexOf("=") > -1) {
					String paramName = token.substring(0, token.indexOf("="));
					String paramValue = token.substring(token.indexOf("=") + 1);
					try {
						paramValue = URLDecoder.decode(paramValue, UTF8);
					} catch (UnsupportedEncodingException e) {
						LOGGER.warn("UnsupportedEncodingException while decoding paramValue:'" + paramValue + "', using " + UTF8, e);
					}
					paramsMap.put(paramName, paramValue);
				}
			}
		}
		return paramsMap;
	}
	
	public String paramsMapToString(Map<String, String> params) {
		StringBuilder builder = new StringBuilder();
		boolean first = true;
		for (String key : params.keySet()) {
			if (first) {
				first = false;
			} else {
				builder.append("&");
			}
			builder.append(key).append("=").append(params.get(key));
		}
		return builder.toString();
	}
	
}
