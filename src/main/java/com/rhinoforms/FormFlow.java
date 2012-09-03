package com.rhinoforms;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import javax.xml.xpath.XPathExpressionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.rhinoforms.serverside.InputPojo;

public class FormFlow implements Serializable {

	private int id;
	private Document dataDocument;
	private String flowDocBase;
	private Map<String, List<Form>> formLists;
	
	/**
	 * TODO: use navigationStack to keep track of where we are in nested flow. Editing a new
	 * additional driver > new claim I have lost the driver index. Would also be good to
	 * be able to just call next, cancel or back at the ends of a sub-list and be
	 * dropped back into the right place without giving an actionTarget.
	 */
	private Stack<NavigationStackItem> navigationStack;
	
	private List<Form> currentFormList;
	private Form currentForm;
	private String currentDocBase;
	private List<InputPojo> currentInputPojos;
	private Map<String, FieldSourceProxy> fieldSourceProxies;

	public static final String NEXT_ACTION = "next";
	public static final String BACK_ACTION = "back";
	public static final String CANCEL_ACTION = "cancel";
	public static final String DELETE_ACTION = "_delete";
	public static final String FINISH_ACTION = "finish";

	private static final Logger logger = LoggerFactory.getLogger(FormFlow.class);
	private static final long serialVersionUID = -5683469121328756822L;

	public FormFlow() {
		this.id = (int) (Math.random() * 100000000f);
		this.formLists = new HashMap<String, List<Form>>();
		this.navigationStack = new Stack<NavigationStackItem>();
		this.fieldSourceProxies = new HashMap<String, FieldSourceProxy>();
	}

	public String navigateToFirstForm(DocumentHelper documentHelper) throws ActionError {
		this.currentFormList = formLists.get("main");
		this.currentForm = currentFormList.iterator().next();
		updateDocBase(documentHelper, new HashMap<String, String>(), null);
		return currentForm.getPath();
	}

	public String doAction(String action, Map<String, String> paramsFromFontend, DocumentHelper documentHelper) throws ActionError {
		clearFormSpecificResources();

		FlowAction flowAction = getAction(action);
		Map<String, String> actionParams = filterActionParams(paramsFromFontend, flowAction.getParams());
		String actionName = flowAction.getName();
		String actionTarget = flowAction.getTarget();
		String lastDocBase = getCurrentDocBase();
		if (actionTarget.isEmpty()) {
			if (actionName.equals(NEXT_ACTION)) {
				int nextIndex = currentForm.getIndexInList() + 1;
				if (nextIndex < currentFormList.size()) {
					currentForm = currentFormList.get(nextIndex);
				} else {
					throw new ActionError(
							"Can not do 'next', end of current form list reached. Specify an action-target to change form lists within this flow.");
				}
			} else if (actionName.equals(BACK_ACTION)) {
				int nextIndex = currentForm.getIndexInList() - 1;
				if (nextIndex >= 0) {
					currentForm = currentFormList.get(nextIndex);
				} else {
					// If the author intended to jump to another flow list they should specify an action-target
					throw new ActionError(
							"Can not do 'back', start of current form list reached. Specify an action-target to change form lists within this flow.");
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
		updateDocBase(documentHelper, actionParams, lastDocBase);
		return currentForm.getPath();
	}

	private void updateDocBase(DocumentHelper documentHelper, Map<String, String> actionParams, String lastDocBase) throws ActionError {
		String currentFormDocBase = currentForm.getDocBase();
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
				setCurrentDocBase(documentHelper.resolveXPathIndexesForAction(currentFormDocBase, actionParams, dataDocument));
			} else {
				setCurrentDocBase(getFlowDocBase());
			}
			if (lastDocBase != null && !lastDocBase.equals(getCurrentDocBase())) {
				documentHelper.deleteNodeIfEmptyRecurseUp(dataDocument, lastDocBase);
			}
			documentHelper.createNodeIfNotThere(dataDocument, getCurrentDocBase());
		} catch (DocumentHelperException e) {
			throw new ActionError("Problem with docBase index alias.", e);
		}
	}

	private void clearFormSpecificResources() {
		clearFieldSourceProxies();
	}

	public FlowAction getAction(String action) throws ActionError {
		Map<String, FlowAction> actions = currentForm.getActions();
		if (actions.containsKey(action)) {
			return actions.get(action);
		} else {
			StringBuilder builder = new StringBuilder();
			builder.append("Action not valid for the current form. ");
			builder.append("Current formId: ").append(currentForm.getId()).append(", ");
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

	public String getCurrentPath() {
		return currentForm.getPath();
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
		return id;
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
		return currentDocBase;
	}

	public void setCurrentDocBase(String docBase) {
		if (!docBase.equals(this.currentDocBase)) {
			logger.debug("New doc base: {}", docBase);
		}
		this.currentDocBase = docBase;
	}

	public String getFlowDocBase() {
		return flowDocBase;
	}

	public void setFlowDocBase(String flowDocBase) {
		this.flowDocBase = flowDocBase;
		setCurrentDocBase(flowDocBase);
	}

}
