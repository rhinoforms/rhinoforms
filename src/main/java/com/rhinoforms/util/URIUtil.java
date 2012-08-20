package com.rhinoforms.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class URIUtil {

	final Logger logger = LoggerFactory.getLogger(URIUtil.class);
	private static final String UTF8 = "UTF-8";

	public Map<String, String> paramsStringToMap(String params) {
		Map<String, String> paramsMap = new HashMap<String, String>();
		if (params != null) {
			StringTokenizer st = new StringTokenizer(params, "&");
			String paramValue = null;
			try {
				while (st.hasMoreTokens()) {
					String token = st.nextToken();
					if (token.indexOf("=") > -1) {
						String paramName = token.substring(0, token.indexOf("="));
						paramValue = token.substring(token.indexOf("=") + 1);
						paramValue = URLDecoder.decode(paramValue, UTF8);
						paramsMap.put(paramName, paramValue);
					}
				}
			} catch (UnsupportedEncodingException e) {
				logger.warn("UnsupportedEncodingException while decoding paramValue:'{}', using " + UTF8, paramValue, e);
			}
		}
		return paramsMap;
	}

	public String paramsMapToString(Map<String, String> params) {
		StringBuilder builder = new StringBuilder();
		boolean first = true;
		String paramKey = null;
		String paramValue = null;
		try {
			for (String key : params.keySet()) {
				if (first) {
					first = false;
				} else {
					builder.append("&");
				}
				paramKey = key;
				paramValue = params.get(key);
				builder.append(URLEncoder.encode(key, UTF8)).append("=").append(URLEncoder.encode(paramValue, UTF8));
			}
		} catch (UnsupportedEncodingException e) {
			logger.warn("UnsupportedEncodingException while encoding paramKey:'{}', paramValue:'{}', using {}", new String[] {paramKey, paramValue, UTF8});
		}
		return builder.toString();
	}

}
