package com.rhinoforms.util;

public class StringUtils {
	
	public static boolean isStringTrueNullSafe(String string) {
		return "true".equalsIgnoreCase(string);
	}
	
}