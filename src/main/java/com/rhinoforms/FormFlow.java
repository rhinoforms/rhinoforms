package com.rhinoforms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;

import org.apache.log4j.Logger;
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
	private Document dataDocument;
	private String docBase;
	private String flowDocBase;
	private Map<String, FieldSourceProxy> fieldSourceProxies;

	private DocumentHelper documentHelper;

	public static final String NEXT_ACTION = "next";
	public static final String BACK_ACTION = "back";
	public static final String CANCEL_ACTION = "cancel";
	public static final String FINISH_ACTION = "finish";

	private static final Logger LOGGER = Logger.getLogger(FormFlow.class);

	public FormFlow(Scriptable scope) {
		this.id = (int) (Math.random() * 100000000f);
		this.scope = scope;
		this.formLists = new HashMap<String, List<Form>>();
		this.fieldSourceProxies = new HashMap<String, FieldSourceProxy>();
	}

	public String navigateToFirstForm() {
		this.currentFormList = formLists.get("main");
		this.currentForm = currentFormList.iterator().next();
		return currentForm.getPath();
	}

	public String doAction(String action, Map<String, String> paramsFromFontend) throws ActionError {
		clearFormSpecificResources();
		
		FlowAction flowAction = getAction(action);
		Map<String, String> actionParams = filterActionParams(paramsFromFontend, flowAction.getParams());
		String actionName = flowAction.getName();
		String actionTarget = flowAction.getTarget();
		if (actionTarget.isEmpty()) {
			if (actionName.equals(NEXT_ACTION)) {
				currentForm = currentFormList.get(currentForm.getIndexInList() + 1);
			} else if (actionName.equals(BACK_ACTION)) {
				currentForm = currentFormList.get(currentForm.getIndexInList() - 1);
			} else {
				if (actionName.equals(FINISH_ACTION)) {
					return null;
				}
			}
		} else if (actionTarget.equals("_delete")) {
			String xpath = actionParams.get("xpath");
			try {
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
		String currentFormDocBase = currentForm.getDocBase();
		if (currentFormDocBase != null) {
			try {
				setDocBase(documentHelper.resolveXPathIndexesForAction(currentFormDocBase, actionParams, dataDocument));
			} catch (DocumentHelperException e) {
				throw new ActionError("Problem with docBase index alias.", e);
			}
		} else {
			setDocBase(getFlowDocBase());
		}
		return currentForm.getPath();
	}

	private void clearFormSpecificResources() {
		clearFieldSourceProxies();
	}

	public FlowAction getAction(String action) throws ActionError {
		Map<String, FlowAction> actions = currentForm.getActions();
		if (actions.containsKey(action)) {
			return actions.get(action);
		} else {
			throw new ActionError("Action not valid for the current form.");
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

	public Document getDataDocument() {
		return dataDocument;
	}

	public void setDataDocument(Document dataDocument) {
		this.dataDocument = dataDocument;
	}

	public String getDocBase() {
		return docBase;
	}

	public void setDocBase(String docBase) {
		if (!docBase.equals(this.docBase)) {
			LOGGER.debug("New doc base: " + docBase);
		}
		this.docBase = docBase;
	}

	public String getFlowDocBase() {
		return flowDocBase;
	}

	public void setFlowDocBase(String flowDocBase) {
		this.flowDocBase = flowDocBase;
		setDocBase(flowDocBase);
	}

	public DocumentHelper getDocumentHelper() {
		return documentHelper;
	}

	public void setDocumentHelper(DocumentHelper documentHelper) {
		this.documentHelper = documentHelper;
	}

}
