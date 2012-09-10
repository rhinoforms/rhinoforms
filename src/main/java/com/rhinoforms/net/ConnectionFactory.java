package com.rhinoforms.net;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;

public interface ConnectionFactory {

	HttpURLConnection openConnection(String url) throws MalformedURLException, IOException;
	
}
