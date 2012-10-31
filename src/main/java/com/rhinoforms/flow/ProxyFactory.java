package com.rhinoforms.flow;

import java.util.UUID;

public class ProxyFactory {

	public FieldSourceProxy createFlowProxy(String formPath, String fieldName, String url) {
		String proxyPath = UUID.nameUUIDFromBytes((formPath + fieldName).getBytes()).toString();
		return new FieldSourceProxy(proxyPath, url);
	}

}
