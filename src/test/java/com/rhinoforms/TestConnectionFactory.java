package com.rhinoforms;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.output.ByteArrayOutputStream;

import com.rhinoforms.net.ConnectionFactory;

public class TestConnectionFactory implements ConnectionFactory {

	private String recordedRequestUrl;
	private ByteArrayOutputStream recordedRequestStream = new ByteArrayOutputStream();
	private Map<String, String> recordedRequestProperties = new HashMap<String, String>();

	private String testResponseString = "<defaultTestResult/>";
	private int testResponseCode = 200;
	private String testContentType;
	private String testResponseMessage = "";

	@Override
	public HttpURLConnection openConnection(String url)
			throws MalformedURLException, IOException {

		this.recordedRequestUrl = url;

		return new HttpURLConnection(new URL(url)) {

			@Override
			public void connect() throws IOException {
			}

			@Override
			public boolean usingProxy() {
				return false;
			}

			@Override
			public void disconnect() {
			}

			@Override
			public OutputStream getOutputStream() throws IOException {
				return recordedRequestStream;
			}

			@Override
			public int getResponseCode() throws IOException {
				return testResponseCode;
			}

			@Override
			public String getContentType() {
				return testContentType;
			}

			@Override
			public String getResponseMessage() throws IOException {
				return testResponseMessage;
			}

			@Override
			public InputStream getInputStream() throws IOException {
				return new ByteArrayInputStream(testResponseString.getBytes());
			}

			@Override
			public void setRequestProperty(String key, String value) {
				recordedRequestProperties.put(key, value);
			}

		};
	}

	public void setResponseCode(int testResponseCode) {
		this.testResponseCode = testResponseCode;
	}

	public void setContentType(String testContentType) {
		this.testContentType = testContentType;
	}

	public void setResponseMessage(String testResponseMessage) {
		this.testResponseMessage = testResponseMessage;
	}

	public void setResponseString(String resultXmlString) {
		this.testResponseString = resultXmlString;
	}

	public String getRecordedRequestUrl() {
		return recordedRequestUrl;
	}

	public ByteArrayOutputStream getRecordedRequestStream() {
		return recordedRequestStream;
	}

	public void resetRecordedRequestStream() {
		recordedRequestStream.reset();
	}

	public Map<String, String> getRecordedRequestProperties() {
		return recordedRequestProperties;
	}

}
