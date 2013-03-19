package com.rhinoforms.flow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubmissionTimeKeeper {

	private Map<String, List<Integer>> averages;
	private int averageOf = 3;
	
	public SubmissionTimeKeeper() {
		averages = new HashMap<String, List<Integer>>();
	}
	
	public void recordTimeTaken(String formId, String action, List<Integer> times) {
		String key = getKey(formId, action);
		List<Integer> previousTimes = averages.get(key);
		
		if (previousTimes == null || previousTimes.size() != times.size()) {
			averages.put(key, times);
		} else {
			for (int i = 0; i < times.size(); i++) {
				previousTimes.set(i, ((previousTimes.get(i) / averageOf) * (averageOf - 1)) + (times.get(i) / averageOf));
			}
		}
	}

	public List<Integer> getEstimate(String formId, String action) {
		return averages.get(getKey(formId, action));
	}

	private String getKey(String formId, String action) {
		return formId + "|" + action;
	}
	
}
