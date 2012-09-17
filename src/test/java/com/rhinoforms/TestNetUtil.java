package com.rhinoforms;

import java.io.IOException;

import com.rhinoforms.js.NetUtil;

public class TestNetUtil implements NetUtil {

	private String urlRequested;
	private Object returnObject;
	
	@Override
	public Object httpGetJsObject(String urlString) throws IOException {
		urlRequested = urlString;
		return returnObject;
	}
	
	public void setReturnObject(Object returnObject) {
		this.returnObject = returnObject;
	}
	
	public String getUrlRequested() {
		return urlRequested;
	}

}
