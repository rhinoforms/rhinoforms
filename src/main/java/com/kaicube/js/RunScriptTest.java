package com.kaicube.js;

import java.io.FileReader;
import java.io.IOException;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

public class RunScriptTest {
	public static void main(String[] args) throws IOException {
		Context cx = Context.enter();
		try {
			Scriptable scope = cx.initStandardObjects();

			String script = "src/main/webapp/js/script.js";
			FileReader fileReader = new FileReader(script);
			cx.evaluateReader(scope, fileReader, script, 1, null);
			
			cx.evaluateString(scope, "var form = {a:'123'}", "<cmd>", 1, null);
			
			// Now evaluate the string we've colected.
			Object result = cx.evaluateString(scope, "matches('123', form.a)", "<cmd>", 1, null);

			// Convert the result to a string and print it.
			System.err.println(Context.toString(result));
		} finally {
			// Exit from the context.
			Context.exit();
		}
	}
}
