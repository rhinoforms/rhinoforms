package com.rhinoforms.flow;

import java.util.HashSet;
import java.util.Set;

public enum FlowActionType {

	NEXT, BACK, CANCEL, DELETE, FINISH;

	private static final Set<String> stringValues;
	static {
		stringValues = new HashSet<String>();
		for (FlowActionType flowActionType : FlowActionType.values()) {
			stringValues.add(flowActionType.toString());
		}
	}
	
	public static FlowActionType safeValueOf(String label) {
		if (label != null && !label.isEmpty() && stringValues.contains(label.toLowerCase())) {
			return FlowActionType.valueOf(label.toUpperCase());
		}
		return null;
	}

	@Override
	public String toString() {
		return super.toString().toLowerCase();
	}
	
}
