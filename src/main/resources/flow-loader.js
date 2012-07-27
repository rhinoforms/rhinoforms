function loadFlow(flowMap) {
	
	formFlow.setDocumentBasePath(flowMap.docBase);
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
				actionsJ.put(actionName, actionTarget);
			}
			var formJ = new com.rhinoforms.Form(form.id, form.url, actionsJ, formIndex);
			formListJ.add(formJ);
		}
		formFlow.addFormList(formListName, formListJ);
	}
	
}