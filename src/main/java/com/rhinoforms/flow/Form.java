package com.rhinoforms.flow;

import java.io.Serializable;
import java.util.Map;

public class Form implements Serializable {
	
	private String id;
	private String path;
	private Map<String, FlowAction> actions;
	private String docBase;
	private int indexInList;
	private static final long serialVersionUID = 4473864389096510334L;

	public Form(String id, String path, Map<String, FlowAction> actions, int indexInList) {
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

	public Map<String, FlowAction> getActions() {
		return actions;
	}

	public void setActions(Map<String, FlowAction> actions) {
		this.actions = actions;
	}

	public int getIndexInList() {
		return indexInList;
	}

	public void setIndexInList(int indexInList) {
		this.indexInList = indexInList;
	}

	public String getDocBase() {
		return docBase;
	}

	public void setDocBase(String docBase) {
		this.docBase = docBase;
	}

}
