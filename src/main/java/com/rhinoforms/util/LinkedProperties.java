package com.rhinoforms.util;

import java.util.*;

public class LinkedProperties extends Properties {

	private static final long serialVersionUID = 6565227944227691622L;
	private final LinkedHashSet<Object> keys = new LinkedHashSet<>();

	@Override
	public Enumeration<Object> keys() {
		return Collections.enumeration(keys);
	}

	@Override
	public Set<Object> keySet() {
		return keys;
	}

	@Override
	public Object put(Object key, Object value) {
		keys.add(key);
		return super.put(key, value);
	}

}