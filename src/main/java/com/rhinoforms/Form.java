package com.rhinoforms;

import java.util.Map;

public class Form {

	private String id;
	private String path;
	private Map<String, String> actions;
	private int indexInList;
	
	public Form(String id, String path, Map<String, String> actions, int indexInList) {
		super();
		this.id = id;
		this.path = path;
		this.actions = actions;
		this.indexInList = indexInList;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public Map<String, String> getActions() {
		return actions;
	}

	public void setActions(Map<String, String> actions) {
		this.actions = actions;
	}
	
	public int getIndexInList() {
		return indexInList;
	}
	
	public void setIndexInList(int indexInList) {
		this.indexInList = indexInList;
	}

}
