/**
 * Useful objects in scope:
 * 		rf - the RhinoForms object
 * 		netUtil - a java object for downloading resources (com.rhinoforms.js.NetUtil)
 */

rf.registerValidationKeyword("fromSource", function(value, rfAttributes) {
	var valid = false;
	if (value && value.length >= 3) {
		var source = rfAttributes["rf.source"];
		source = source.replace("[value]", value.toLowerCase().substring(0, 3));
		var array = netUtil.httpGetJsObject(source);
		if (array) {
			for (var i in array) {
				if (value == array[i][1]) {
					valid = true;
					break;
				}
			}
		}
	}
	
	if (valid == false) {
		return "This should be a value from the drop-down list."
	}
	
});
rf.setupError = function(message) {
	throw new Error(message);
}