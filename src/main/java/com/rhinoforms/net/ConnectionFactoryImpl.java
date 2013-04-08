package com.rhinoforms.net;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.codec.binary.Base64;

public class ConnectionFactoryImpl implements ConnectionFactory {

	@Override
	public HttpURLConnection openConnection(String url) throws MalformedURLException, IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		
		// Add Auth
		if (url.contains("@")) {
			// Format: http://user:pass@hostname/
			String usernamePassword = url.substring(url.indexOf("//") + 2, url.lastIndexOf("@"));
			String[] usernamePasswordParts = usernamePassword.split(":");
			connection.setRequestProperty("Authorization", "Basic " + base64Encode(usernamePasswordParts[0] + ":" + usernamePasswordParts[1]));
		}
		
		return connection;
	}
	
	private static String base64Encode(String source) {
		return new String(new Base64().encode(source.getBytes())).trim();
	}
	
}
