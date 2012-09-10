package com.rhinoforms;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;

import com.rhinoforms.resourceloader.ResourceLoader;
import com.rhinoforms.serverside.InputPojo;

public class FormFlow implements Serializable {

	private int flowId;
	private String flowDocBase;
	private Map<String, List<Form>> formLists;
	private Document dataDocument;
	
	private Stack<FlowNavigationLevel> navigationStack;
	private List<InputPojo> currentInputPojos;
	private Map<String, FieldSourceProxy> fieldSourceProxies;
	private String resourcesBase;
	private RemoteSubmissionHelper remoteSubmissionHelper;

	public static final String FIRST_ACTION = "first";
	public static final String NEXT_ACTION = "next";
	public static final String BACK_ACTION = "back";
	public static final String CANCEL_ACTION = "cancel";
	public static final String DELETE_ACTION = "_delete";
	public static final String FINISH_ACTION = "finish";

	private static final long serialVersionUID = -5683469121328756822L;

	public FormFlow(ResourceLoader resourceLoader) {
		this.flowId = (int) (Math.random() * 100000000f);
		this.formLists = new HashMap<String, List<Form>>();
		this.navigationStack = new Stack<FlowNavigationLevel>();
		this.fieldSourceProxies = new HashMap<String, FieldSourceProxy>();
		this.remoteSubmissionHelper = new RemoteSubmissionHelper(resourceLoader);
	}

	public String navigateToFirstForm(DocumentHelper documentHelper) throws ActionError {
		String listId = "main";
		List<Form> formList = formLists.get(listId);
		FlowNavigationLevel currentNavigationLevel = new FlowNavigationLevel(formList, formList.iterator().next());
		navigationStack.push(currentNavigationLevel);
		String newDocBase = resolveNewDocBase(currentNavigationLevel.getCurrentForm().getDocBase(), null, null, documentHelper);
		currentNavigationLevel.setDocBase(newDocBase);
		return currentNavigationLevel.getCurrentForm().getPath();
	}

	public String doAction(String action, Map<String, String> paramsFromFontend, DocumentHelper documentHelper) throws ActionError {
		clearPreviousFormResources();

		FlowAction flowAction = getAction(action);
		Map<String, String> actionParams = filterActionParams(paramsFromFontend, flowAction.getParams());
		String actionName = flowAction.getName();
		String actionTarget = flowAction.getTarget();
		String lastDocBase = getCurrentDocBase();
		boolean movedUpNavStack = false;
		
		FlowNavigationLevel currentNavigationLevel = getCurrentNavigationLevel();
		
		Submission submission = flowAction.getSubmission();
		if (submission != null) {
			try {
				remoteSubmissionHelper.handleSubmission(submission, dataDocument);
			} catch (RemoteSubmissionHelperException e) {
				throw new ActionError("Remote submission failed.", e);
			}
		}
		
		if (actionTarget.isEmpty()) {
			if (actionName.equals(NEXT_ACTION) || actionName.equals(BACK_ACTION) || actionName.equals(CANCEL_ACTION)) {
				// Moving forward or back in list. May pop up to next level if end of current list.
				int currentFormIndexInList = currentNavigationLevel.getCurrentForm().getIndexInList();
				int nextIndex;
				if (actionName.equals(NEXT_ACTION)) {
					nextIndex = currentFormIndexInList + 1;
				} else {
					nextIndex = currentFormIndexInList - 1;
				}
				if (isValidIndex(nextIndex, currentNavigationLevel.getCurrentFormList().size())) {
					changeFormInCurrentList(nextIndex);
				} else {
					if (navigationStack.size() > 1) {
						currentNavigationLevel = moveUpNavStack();
						movedUpNavStack = true;
					} else {
						throw new ActionError("Can not perform action, there are no more forms in the current list and no more levels on the stack.");
					}
				}
			} else {
				if (actionName.equals(FINISH_ACTION)) {
					return null;
				}
			}
		} else if (actionTarget.equals(DELETE_ACTION)) {
			String xpath = actionParams.get("xpath");
			try {
				if (!xpath.startsWith("/")) {
					xpath = getCurrentDocBase() + "/" + xpath;
				}
				xpath = documentHelper.resolveXPathIndexesForAction(xpath, actionParams, dataDocument);
				documentHelper.deleteNodes(xpath, dataDocument);
			} catch (DocumentHelperException e) {
				throw new ActionError("Problem building delete action.", e);
			} catch (XPathExpressionException e) {
				throw new ActionError("Problem deleting nodes.", e);
			}
		} else {
			// Moving to a named form
			List<Form> formList = currentNavigationLevel.getCurrentFormList();
			String actionTargetFormId;
			if (actionTarget.contains(".")) {
				// Navigating to another form list
				String[] actionTargetParts = actionTarget.split("\\.");
				formList = formLists.get(actionTargetParts[0]);
				actionTargetFormId = actionTargetParts[1];
				currentNavigationLevel = new FlowNavigationLevel(formList, null);
				navigationStack.push(currentNavigationLevel);
			} else {
				actionTargetFormId = actionTarget;
			}
			Form nextForm = null;
			for (Form form : formList) {
				if (form.getId().equals(actionTargetFormId)) {
					nextForm = form;
				}
			}
			if (nextForm != null) {
				currentNavigationLevel.setCurrentForm(nextForm);
			} else {
				throw new ActionError("Did not find form with id '" + actionTargetFormId + "'.");
			}
		}
		
		if (!movedUpNavStack) {
			String newDocBase = resolveNewDocBase(currentNavigationLevel.getCurrentForm().getDocBase(), actionParams, lastDocBase, documentHelper);
			currentNavigationLevel.setDocBase(newDocBase);
		}
		prepDataDocument(getCurrentDocBase(), lastDocBase, documentHelper);
		
		return currentNavigationLevel.getCurrentForm().getPath();
	}

	private boolean isValidIndex(int nextIndex, int listSize) {
		return nextIndex >= 0 && nextIndex < listSize;
	}

	private void changeFormInCurrentList(int nextIndex) {
		getCurrentNavigationLevel().setCurrentForm(getCurrentNavigationLevel().getCurrentFormList().get(nextIndex));
	}

	private FlowNavigationLevel moveUpNavStack() {
		// Discard top item in stack
		navigationStack.pop();
		return getCurrentNavigationLevel();
	}

	private String resolveNewDocBase(String currentFormDocBase, Map<String, String> actionParams, String lastDocBase, DocumentHelper documentHelper) throws ActionError {
		String newDocBase;
		try {
			if (currentFormDocBase != null) {
				if (currentFormDocBase.charAt(0) != '/') {
					// docBase is relative to that of the previous form
					if (lastDocBase != null) {
						currentFormDocBase = lastDocBase + "/" + currentFormDocBase;
					} else {
						throw new ActionError("Can not use relative docBase on first form loaded.");
					}
				}
				newDocBase = documentHelper.resolveXPathIndexesForAction(currentFormDocBase, actionParams, dataDocument);
			} else {
				newDocBase = getFlowDocBase();
			}
			return newDocBase;
		} catch (DocumentHelperException e) {
			throw new ActionError("Problem with docBase index alias.", e);
		}
	}
	
	private void prepDataDocument(String newDocBase, String lastDocBase, DocumentHelper documentHelper) throws ActionError {
		try {
			if (lastDocBase != null && !lastDocBase.equals(newDocBase)) {
				documentHelper.deleteNodeIfEmptyRecurseUp(dataDocument, lastDocBase);
			}
			documentHelper.createNodeIfNotThere(dataDocument, newDocBase);
		} catch (DocumentHelperException e) {
			throw new ActionError("Problem with docBase index alias.", e);
		}
	}


	private void clearPreviousFormResources() {
		clearFieldSourceProxies();
	}

	public FlowAction getAction(String action) throws ActionError {
		Map<String, FlowAction> actions = getCurrentNavigationLevel().getCurrentForm().getActions();
		if (actions.containsKey(action)) {
			return actions.get(action);
		} else {
			StringBuilder builder = new StringBuilder();
			builder.append("Action not valid for the current form. ");
			builder.append("Current formId: ").append(getCurrentNavigationLevel().getCurrentForm().getId()).append(", ");
			builder.append("valid actions: ").append(actions.keySet());
			throw new ActionError(builder.toString());
		}
	}

	protected Map<String, String> filterActionParams(Map<String, String> paramsFromFontend, Map<String, String> paramsFromFlowAction) {
		HashMap<String, String> filteredActionParams = new HashMap<String, String>();
		if (paramsFromFlowAction != null) {
			for (String key : paramsFromFlowAction.keySet()) {
				String value = paramsFromFlowAction.get(key);
				if (value.equals("?")) {
					value = paramsFromFontend.get(key);
				}
				filteredActionParams.put(key, value);
			}
		}
		return filteredActionParams;
	}
	
	public String resolveResourcePathIfRelative(String formResourcePath) {
		// Check for and resolve relative form path
		if (formResourcePath.charAt(0) != '/') {
			formResourcePath = resourcesBase + formResourcePath;
		}
		return formResourcePath;
	}
	
	private FlowNavigationLevel getCurrentNavigationLevel() {
		return navigationStack.peek();
	}
	
	public String getCurrentPath() {
		return getCurrentNavigationLevel().getCurrentForm().getPath();
	}

	public void addFormList(String listName, List<Form> formList) {
		this.formLists.put(listName, formList);
	}

	public void addFieldSourceProxy(FieldSourceProxy fieldSourceProxy) {
		fieldSourceProxies.put(fieldSourceProxy.getProxyPath(), fieldSourceProxy);
	}

	public FieldSourceProxy getFieldSourceProxy(String proxyPath) {
		return fieldSourceProxies.get(proxyPath);
	}

	public void clearFieldSourceProxies() {
		fieldSourceProxies.clear();
	}

	public int getId() {
		return flowId;
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

	public Document getDataDocument() {
		return dataDocument;
	}

	public void setDataDocument(Document dataDocument) {
		this.dataDocument = dataDocument;
	}

	public String getCurrentDocBase() {
		return getCurrentNavigationLevel().getDocBase();
	}

	public String getFlowDocBase() {
		return flowDocBase;
	}

	public void setFlowDocBase(String flowDocBase) {
		this.flowDocBase = flowDocBase;
	}

	public void setResourcesBase(String resourcesBase) {
		this.resourcesBase = resourcesBase;
	}

}
