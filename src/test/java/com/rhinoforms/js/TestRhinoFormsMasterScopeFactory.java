package com.rhinoforms.js;

public class TestRhinoFormsMasterScopeFactory extends RhinoFormsMasterScopeFactory {

	private NetUtil testNetUtil;

	public TestRhinoFormsMasterScopeFactory(NetUtil testNetUtil) {
		this.testNetUtil = testNetUtil;
	}
	
	@Override
	NetUtil createNetUtil(JSMasterScope masterScope) {
		return testNetUtil;
	}
	
}
