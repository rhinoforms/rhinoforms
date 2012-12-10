function loadFlow(flowMap) {
	
	this.trim = function(string) {
		return string.replace(/^\s\s*/, '').replace(/\s\s*$/, '')
	}
	
	formFlow.setFlowDocBase(flowMap.docBase);
	if (flowMap.defaultInitalData) {
		formFlow.setDefaultInitialData(flowMap.defaultInitalData);
	}
	if (flowMap.libraries) {
		var libraries = flowMap.libraries;
		var librariesJ = formFlow.getLibraries();
		for (var l in libraries) {
			librariesJ.add(libraries[l]);
		}
	}
	var formLists = flowMap.formLists;
	for (formListName in formLists) {
		var formArray = formLists[formListName];
		var formListJ = new java.util.ArrayList();
		for (var formIndex in formArray) {
			var form = formArray[formIndex];
			var actionsJ = new java.util.HashMap();
			for (var actionIndex in form.actions) {
				var action = form.actions[actionIndex];
				var actionName;
				var actionTarget = "";
				var actionType = null;
				var actionSubmission = null;
				var actionSubmissions = null;
				var actionClearTargetFormDocBase = false;
				if (action instanceof Object) {
					actionName = action.name;
					if (action.target) {
						actionTarget = action.target;
					}
					if (action.type) {
						actionType = action.type;
					}
					if (action.submission) {
						actionSubmission = action.submission;
					}
					if (action.submissions) {
						actionSubmissions = action.submissions;
					}
					if (action.clearTargetFormDocBase && (action.clearTargetFormDocBase == true || action.clearTargetFormDocBase.toLowerCase() == "true")) {
						actionClearTargetFormDocBase = true;
					}
				} else {
					var actionParts = form.actions[actionIndex].split(':');
					actionName = actionParts[0];
					if (actionParts.length > 1) {
						actionTarget = actionParts[1];
					}
				}
				var actionTargetParamsIndex = actionTarget.indexOf("(");
				var actionTargetParamsParts;
				if (actionTargetParamsIndex != -1) {
					var actionTargetParamsString = actionTarget.substring(actionTargetParamsIndex + 1, actionTarget.indexOf(")"));
					actionTargetParamsParts = actionTargetParamsString.split(",");
					actionTarget = actionTarget.substring(0, actionTargetParamsIndex);
				}
				
				var flowActionJ = new com.rhinoforms.flow.FlowAction(actionName, actionTarget);
				for (var actionTargetParamsPartIndex in actionTargetParamsParts) {
					var param = actionTargetParamsParts[actionTargetParamsPartIndex];
					var paramParts = param.split("=");
					flowActionJ.addParam(this.trim(paramParts[0]), this.trim(paramParts[1]));
				}
				if (actionType) {
					flowActionJ.setType(actionType);
				}
				if (actionSubmission || actionSubmissions) {
					if (!actionSubmissions) {
						actionSubmissions = [];
					}
					if (actionSubmission) {
						actionSubmissions.unshift(actionSubmission);
					}
					var submissionsListJ = new java.util.ArrayList();
					for (var s = 0; s < actionSubmissions.length; s++) {
						var thisSubmission = actionSubmissions[s];
						var submissionJ = new com.rhinoforms.flow.Submission(thisSubmission.url);
						if (thisSubmission.method) {
							submissionJ.setMethod(thisSubmission.method);
						}
						if (thisSubmission.rawXmlRequest && (thisSubmission.rawXmlRequest == true || thisSubmission.rawXmlRequest.toLowerCase() == "true")) {
							submissionJ.setRawXmlRequest(true);
						}
						var data = thisSubmission.data;
						if (data) {
							var dataMapJ = submissionJ.getData();
							for (var prop in data) {
								dataMapJ.put(prop, data[prop]);
							}
						}
						if (thisSubmission.resultInsertPoint) {
							submissionJ.setResultInsertPoint(thisSubmission.resultInsertPoint);
						}
						if (thisSubmission.dropRootNode && (thisSubmission.dropRootNode == false || thisSubmission.dropRootNode.toLowerCase() == "false")) {
							submissionJ.setDropRootNode(false);
						}
						if (thisSubmission.preTransform) {
							submissionJ.setPreTransform(formFlow.resolveResourcePathIfRelative(thisSubmission.preTransform));
						}
						if (thisSubmission.postTransform) {
							submissionJ.setPostTransform(formFlow.resolveResourcePathIfRelative(thisSubmission.postTransform));
						}
						submissionsListJ.add(submissionJ);
					}
					flowActionJ.setSubmissions(submissionsListJ);
				}
				flowActionJ.setClearTargetFormDocBase(actionClearTargetFormDocBase);
				actionsJ.put(actionName, flowActionJ);
			}
			var formJ = new com.rhinoforms.flow.Form(form.id, form.url, actionsJ, formIndex);
			if (form.docBase) {
				formJ.setDocBase(form.docBase);
			}
			formListJ.add(formJ);
		}
		formFlow.addFormList(formListName, formListJ);
	}
	
}