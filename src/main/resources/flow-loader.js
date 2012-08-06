function loadFlow(flowMap) {
	
	formFlow.setFlowDocBase(flowMap.docBase);
	var formLists = flowMap.formLists;
	for (formListName in formLists) {
		var formArray = formLists[formListName];
		var formListJ = new java.util.ArrayList();
		for (var formIndex in formArray) {
			var form = formArray[formIndex];
			var actionsJ = new java.util.HashMap();
			for (var actionIndex in form.actions) {
				var actionParts = form.actions[actionIndex].split(':');
				var actionName = actionParts[0];
				var actionTarget = "";
				if (actionParts.length > 1) {
					actionTarget = actionParts[1];
				}
				var actionTargetParamsIndex = actionTarget.indexOf("(");
				var actionTargetParamsParts;
				if (actionTargetParamsIndex != -1) {
					var actionTargetParamsString = actionTarget.substring(actionTargetParamsIndex + 1, actionTarget.indexOf(")"));
					actionTargetParamsParts = actionTargetParamsString.split(",");
					actionTarget = actionTarget.substring(0, actionTargetParamsIndex);
				}
				var flowActionJ = new com.rhinoforms.FlowAction(actionName, actionTarget);
				for (var actionTargetParamsPartIndex in actionTargetParamsParts) {
					var param = actionTargetParamsParts[actionTargetParamsPartIndex];
					var paramParts = param.split("=");
					flowActionJ.addParam(paramParts[0], paramParts[1]);
				}
				actionsJ.put(actionName, flowActionJ);
			}
			var formJ = new com.rhinoforms.Form(form.id, form.url, actionsJ, formIndex);
			if (form.docBase) {
				formJ.setDocBase(form.docBase);
			}
			formListJ.add(formJ);
		}
		formFlow.addFormList(formListName, formListJ);
	}
	
}