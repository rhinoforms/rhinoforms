package com.rhinoforms.js;

import java.io.IOException;
import java.util.Date;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Scriptable;

import com.rhinoforms.TestResourceLoader;
import com.rhinoforms.resourceloader.ResourceLoaderException;
import com.rhinoforms.resourceloader.ResourceLoaderImpl;

public class JSMasterScopeTest {

	private JSMasterScope masterScope;
	private Context context;

	@Before
	public void setup() throws IOException, ResourceLoaderException {
		context = Context.enter();
		TestResourceLoader loader = new TestResourceLoader();
		masterScope = new RhinoFormsMasterScopeFactory().createMasterScope(context, new ResourceLoaderImpl(loader, loader));
	}
	
	@Test
	public void testSharedScopeModificationViaWorkingScopeUsingPrototype() throws IOException {
		Scriptable workingScope = masterScope.createWorkingScope();
		context.evaluateString(workingScope, "Rhinoforms.prototype.getCurrentAction = function() { return 'hello'; }", "test", 1, null);
		Assert.assertNull(context.evaluateString(workingScope, "rf.getCurrentAction();", "test", 1, null));
	}

	@Test(expected = EvaluatorException.class)
	public void testSharedScopeModificationViaWorkingScopeUsingInstance() throws IOException {
		Scriptable workingScope = masterScope.createWorkingScope();
		context.evaluateString(workingScope, "rf.getCurrentAction = function() { return 'hello'; }", "test", 1, null);
	}
	
	@Test
	public void performanceTestNoAssertions() throws IOException {
		long start = new Date().getTime();
		int a = 100;
		for (int i = 0; i < a; i++) {
			masterScope.createWorkingScope();
		}
		System.out.println("Created " + a + " working scopes in " + new Float(new Date().getTime() - start) / 1000 + " seconds");
	}

	@After
	public void after() {
		Context.exit();
	}
	
}
