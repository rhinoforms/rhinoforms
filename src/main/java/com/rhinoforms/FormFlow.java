package com.rhinoforms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mozilla.javascript.Scriptable;
import org.w3c.dom.Document;

import com.rhinoforms.serverside.InputPojo;

public class FormFlow {

	private int id;
	private Scriptable scope;
	private List<InputPojo> currentInputPojos;
	private Map<String, List<Form>> formLists;
	private Form currentForm;
	private List<Form> currentFormList;
	private Map<String, String> data;
	private Document dataDocument;
	private String documentBasePath;
	
	public static final String NEXT_ACTION = "next";
	public static final String BACK_ACTION = "back";
	public static final String FINISH_ACTION = "finish";
	
	public FormFlow(Scriptable scope) {
		this.id = (int) (Math.random() * 100000000f);
		this.scope = scope;
		this.data = new HashMap<String, String>();
		this.formLists = new HashMap<String, List<Form>>();
	}
	
	public FormFlow(Scriptable scope, Document dataDocument) {
		this(scope);
		this.dataDocument = dataDocument;
	}
	
	public String navigateToFirstForm() {
		this.currentFormList = formLists.get("main");
		this.currentForm = currentFormList.iterator().next();
		return currentForm.getPath();
	}
	
	public String navigateFlow(String action) throws NavigationError {
		Map<String, String> actions = currentForm.getActions();
		if (actions.containsKey(action)) {
			String actionTarget = actions.get(action);
			if (actionTarget.isEmpty()) {
				if (action.equals(NEXT_ACTION)) {
					currentForm = currentFormList.get(currentForm.getIndexInList() + 1);
				} else {
					if (action.equals(BACK_ACTION)) {
						currentForm = currentFormList.get(currentForm.getIndexInList() - 1);
					} else {
						if (action.equals(FINISH_ACTION)) {
							return null;
						}
					}
				}
			} else {
				String actionTargetFormId;
				if (actionTarget.contains(".")) {
					String[] actionTargetParts = actionTarget.split("\\.");
					currentFormList = formLists.get(actionTargetParts[0]);
					actionTargetFormId = actionTargetParts[1];
				} else {
					actionTargetFormId = actionTarget;
				}
				for (Form form : currentFormList) {
					if (form.getId().equals(actionTargetFormId)) {
						currentForm = form;
					}
				}
			}
		} else {
			throw new NavigationError("Action not valid for the current form.");
		}
		return currentForm.getPath();
	}
	
	public String getCurrentPath() {
		return currentForm.getPath();
	}
	
	public void addFormList(String listName, List<Form> formList) {
		this.formLists.put(listName, formList);
	}
	
	public int getId() {
		return id;
	}
	
	public Scriptable getScope() {
		return scope;
	}

	public List<InputPojo> getCurrentInputPojos() {
		return currentInputPojos;
	}
	
	public void setCurrentInputPojos(List<InputPojo> currentInputPojos) {
		this.currentInputPojos = currentInputPojos;
	}
	
	public Map<String, List<Form>> getFormLists() {
		return formLists;
	}
	
	public void setFormLists(Map<String, List<Form>> formLists) {
		this.formLists = formLists;
	}
	
	public Map<String, String> getData() {
		return data;
	}
	
	public void setData(Map<String, String> data) {
		this.data = data;
	}
	
	public Document getDataDocument() {
		return dataDocument;
	}
	
	public void setDataDocument(Document dataDocument) {
		this.dataDocument = dataDocument;
	}
	
	public String getDocumentBasePath() {
		return documentBasePath;
	}
	
	public void setDocumentBasePath(String documentBasePath) {
		this.documentBasePath = documentBasePath;
	}

}
