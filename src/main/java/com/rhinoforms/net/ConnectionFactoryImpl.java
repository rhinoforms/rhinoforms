package com.rhinoforms.net;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class ConnectionFactoryImpl implements ConnectionFactory {

	@Override
	public HttpURLConnection openConnection(String url) throws MalformedURLException, IOException {
		return (HttpURLConnection) new URL(url).openConnection();
	}

}
