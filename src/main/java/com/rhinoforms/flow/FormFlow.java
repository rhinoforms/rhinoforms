package com.rhinoforms.flow;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;

import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.rhinoforms.xml.DocumentHelper;
import com.rhinoforms.xml.DocumentHelperException;
import com.rhinoforms.xml.FlowExceptionXPath;

public class FormFlow implements Serializable {

	private String flowId;
	private String flowDocBase;
	private String defaultInitialData;
	private List<String> libraries;
	private Map<String, List<Form>> formLists;
	private Document dataDocument;
	private Properties properties;
	
	private Stack<FlowNavigationLevel> navigationStack;
	private List<InputPojo> currentInputPojos;
	private Map<String, FieldSourceProxy> fieldSourceProxies;
	private String resourcesBase;
	private transient RemoteSubmissionHelper remoteSubmissionHelper;
	private transient SubmissionTimeKeeper submissionTimeKeeper;
	private TransformHelper transformHelper;
		
	private boolean disableInputsOnSubmit;

	private static final Logger LOGGER = LoggerFactory.getLogger(FormFlow.class);
	private static final long serialVersionUID = -5683469121328756822L;

	public FormFlow() {
		this.flowId = (int) (Math.random() * 100000000f) + "";
		this.formLists = new HashMap<String, List<Form>>();
		this.navigationStack = new Stack<FlowNavigationLevel>();
		this.fieldSourceProxies = new HashMap<String, FieldSourceProxy>();
		this.libraries = new ArrayList<String>();
		
		this.disableInputsOnSubmit = true;
	}

	public String navigateToFirstForm(DocumentHelper documentHelper) throws FlowExceptionActionError, FlowExceptionXPath {
		String listId = "main";
		List<Form> formList = formLists.get(listId);
		FlowNavigationLevel currentNavigationLevel = new FlowNavigationLevel(formList, formList.iterator().next());
		navigationStack.push(currentNavigationLevel);
		String newDocBase = resolveNewDocBase("NewFlowFirstForm", currentNavigationLevel.getCurrentForm().getDocBase(), null, null, documentHelper);
		currentNavigationLevel.setDocBase(newDocBase);
		prepDataDocument(getCurrentDocBase(), null, false, documentHelper);
		return currentNavigationLevel.getCurrentForm().getPath();
	}

	public String doAction(String actionName, Map<String, String> paramsFromFontend, DocumentHelper documentHelper) throws FlowExceptionActionError, FlowExceptionXPath, IOException, RemoteSubmissionHelperException, TransformerException, DocumentHelperException, TransformHelperException {
		clearPreviousFormResources();

		FlowAction flowAction = getAction(actionName);
		Map<String, String> actionParams = filterActionParams(paramsFromFontend, flowAction.getParams());
		FlowActionType actionType = flowAction.getType();
		String actionTarget = flowAction.getTarget();
		String lastDocBase = getCurrentDocBase();
		boolean movedUpNavStack = false;
		
		FlowNavigationLevel currentNavigationLevel = getCurrentNavigationLevel();
		
		List<Submission> submissions = flowAction.getSubmissions();
		if (submissions != null) {
			List<Integer> times = new ArrayList<Integer>();
			long startTime;
			for (Submission submission : submissions) {
				startTime = new Date().getTime();
				Map<String, String> xsltParameters = new HashMap<String, String>();
				xsltParameters.put("rf.flowId", flowId);
				xsltParameters.put("rf.formId", getCurrentFormId());
				xsltParameters.put("rf.actionName", actionName);
				remoteSubmissionHelper.handleSubmission(submission, xsltParameters, this);
				times.add((int) (startTime - new Date().getTime()));
			}
			LOGGER.debug("submissionTimeKeeper {} formId {} actionName {} times {}", new Object[] {submissionTimeKeeper, getCurrentFormId(), actionName, times});
			submissionTimeKeeper.recordTimeTaken(getCurrentFormId(), actionName, times);
		}
		
		if (flowAction.getDataDocTransform() != null){
			String docResult = transformHelper.handleTransform(flowAction.getDataDocTransform(), true, null, dataDocument, "transforming DataDocument using action transform.");
			dataDocument = documentHelper.stringToDocument(docResult);
		}
		
		if (actionTarget.isEmpty()) {
			if (actionType == FlowActionType.NEXT || actionType == FlowActionType.BACK || actionType == FlowActionType.CANCEL) {
				// Moving forward or back in list. May pop up to next level if end of current list.
				int currentFormIndexInList = currentNavigationLevel.getCurrentForm().getIndexInList();
				int nextIndex;
				if (actionType == FlowActionType.NEXT) {
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
						throw new FlowExceptionActionError("Can not perform action, there are no more forms in the current list and no more levels on the stack.");
					}
				}
			} else {
				if (actionType == FlowActionType.FINISH) {
					return null;
				}
			}
		} else if (actionTarget.equals(FlowActionType.DELETE.toString())) {
			String xpath = actionParams.get("xpath");
			if (!xpath.startsWith("/")) {
				xpath = getCurrentDocBase() + "/" + xpath;
			}
			xpath = documentHelper.resolveXPathIndexesForAction(actionName, xpath, actionParams, dataDocument);
			try {
				documentHelper.deleteElements(xpath, dataDocument);
			} catch (XPathExpressionException e) {
				throw new FlowExceptionXPath("Problem with delete action named '" + actionName + "'. The XPath failed to compile or execute. XPath '" + xpath + "'.", e);
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
				throw new FlowExceptionActionError("Did not find form with id '" + actionTargetFormId + "'.");
			}
		}
		
		if (!movedUpNavStack) {
			String newDocBase = resolveNewDocBase(actionName, currentNavigationLevel.getCurrentForm().getDocBase(), actionParams, lastDocBase, documentHelper);
			currentNavigationLevel.setDocBase(newDocBase);
		}
		prepDataDocument(getCurrentDocBase(), lastDocBase, flowAction.isClearTargetFormDocBase(), documentHelper);
		
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

	private String resolveNewDocBase(String actionName, String currentFormDocBase, Map<String, String> actionParams, String lastDocBase, DocumentHelper documentHelper) throws FlowExceptionActionError, FlowExceptionXPath {
		String newDocBase;
		if (currentFormDocBase != null) {
			if (currentFormDocBase.charAt(0) != '/') {
				// docBase is relative to that of the previous form
				if (lastDocBase != null) {
					currentFormDocBase = lastDocBase + "/" + currentFormDocBase;
				} else {
					throw new FlowExceptionActionError("Can not use relative docBase on first form loaded.");
				}
			}
			newDocBase = documentHelper.resolveXPathIndexesForAction(actionName, currentFormDocBase, actionParams, dataDocument);
		} else {
			newDocBase = getFlowDocBase();
		}
		return newDocBase;
	}
	
	private void prepDataDocument(String newDocBase, String lastDocBase, boolean clearTargetFormDocBase, DocumentHelper documentHelper) throws FlowExceptionXPath {
		String task = null;
		try {
			if (lastDocBase != null && !lastDocBase.equals(newDocBase)) {
				task = "deleting any empty elements left from previous form in the data document.";
				documentHelper.deleteElementIfEmptyRecurseUp(dataDocument, lastDocBase);
			}
			if (clearTargetFormDocBase) {
				task = "clearing the target form docBase in the data document.";
				documentHelper.deleteElements(newDocBase, dataDocument);
			}
			task = "creating docBase element in the data document.";
			documentHelper.createElementIfNotThere(dataDocument, newDocBase);
		} catch (XPathExpressionException e) {
			throw new FlowExceptionXPath("Error while " + task, e);
		}
	}

	private void clearPreviousFormResources() {
		clearFieldSourceProxies();
	}

	public FlowAction getAction(String action) throws FlowExceptionActionError {
		Map<String, FlowAction> actions = getCurrentNavigationLevel().getCurrentForm().getActions();
		if (actions.containsKey(action)) {
			return actions.get(action);
		} else {
			StringBuilder builder = new StringBuilder();
			builder.append("Action not valid for the current form. ");
			builder.append("Current formId: ").append(getCurrentNavigationLevel().getCurrentForm().getId()).append(", ");
			builder.append("valid actions: ").append(actions.keySet()).append(", ");
			builder.append("requested action: ").append(action);
			throw new FlowExceptionActionError(builder.toString());
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
	
	public String getCurrentFormId() {
		return getCurrentNavigationLevel().getCurrentForm().getId();
	}

	public Map<String, FlowAction> getCurrentActions() {
		return getCurrentNavigationLevel().getCurrentForm().getActions();
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

	public String getId() {
		return flowId;
	}
	
	public String getDefaultInitialData() {
		return defaultInitialData;
	}
	
	public void setDefaultInitialData(String defaultInitalData) {
		this.defaultInitialData = defaultInitalData;
	}

	public List<String> getLibraries() {
		return libraries;
	}
	
	public void setLibraries(List<String> libraries) {
		this.libraries = libraries;
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
	
	public void setRemoteSubmissionHelper(RemoteSubmissionHelper remoteSubmissionHelper) {
		this.remoteSubmissionHelper = remoteSubmissionHelper;
	}
	
	public void setSubmissionTimeKeeper(SubmissionTimeKeeper submissionTimeKeeper) {
		this.submissionTimeKeeper = submissionTimeKeeper;
	}
	
	public void setTransformHelper(TransformHelper transformHelper) {
		this.transformHelper = transformHelper;
	}

	public void setDisableInputsOnSubmit(boolean disableInputsOnSubmit) {
		this.disableInputsOnSubmit = disableInputsOnSubmit;
	}
	
	public boolean isDisableInputsOnSubmit() {
		return disableInputsOnSubmit;
	}
	
	public void setProperties(Properties properties) {
		this.properties = properties;
	}
	
	public Properties getProperties() {
		return properties;
	}
	
}
