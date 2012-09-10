package com.rhinoforms;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;

import org.apache.commons.io.output.ByteArrayOutputStream;

import com.rhinoforms.net.ConnectionFactory;

public class TestConnectionFactory implements ConnectionFactory {

	private String resultXmlString = "<defaultTestResult/>";
	private int testResponseCode = 200;
	private String testResponseMessage = "";
	private ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	
	@Override
	public HttpURLConnection openConnection(String url) throws MalformedURLException, IOException {
		return new HttpURLConnection(null) {
			
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
				return byteArrayOutputStream;
			}
			
			@Override
			public int getResponseCode() throws IOException {
				return testResponseCode;
			}
			
			@Override
			public String getResponseMessage() throws IOException {
				return testResponseMessage;
			}
			
			@Override
			public InputStream getInputStream() throws IOException {
				return new ByteArrayInputStream(resultXmlString.getBytes());
			}
			
		};
	}
	
	public void setTestResponseCode(int testResponseCode) {
		this.testResponseCode = testResponseCode;
	}
	
	public void setTestResponseMessage(String testResponseMessage) {
		this.testResponseMessage = testResponseMessage;
	}
	
	public void setResultXmlString(String resultXmlString) {
		this.resultXmlString = resultXmlString;
	}
	
	public ByteArrayOutputStream getByteArrayOutputStream() {
		return byteArrayOutputStream;
	}
	
	public void resetByteArrayOutputStream() {
		byteArrayOutputStream.reset();
	}
	
}
