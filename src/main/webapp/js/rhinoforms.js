function Rhinoforms() {
	
	var servletUrl = "rhinoforms";
	
	// Should be configurable
	this.alertOnSetupError = true;
	
	// For internal use
	var validationKeywords;
	var customTypes;
	var onFormLoadFunctions = [];
	
	this.init = function() {
		validationKeywords = {};
		customTypes = {};
		
		// Enable the 'required' validation keyword
		this.registerValidationKeyword("required", function(value) {
			if (!value) {
				return "This value is required.";
			}
		});

		// Enable the 'email' validation function
		this.registerValidationKeyword("email", function(value) {
			if (value) {
				var regex = /^([A-Za-z0-9_\-\.])+\@([A-Za-z0-9_\-\.])+\.([A-Za-z]{2,4})$/;
				if (regex.test(value) != true) {
					return "This doesn't seem to be a valid email address.";
				}
			}
		});
		
		// Enable the 'fromSource' validation keyword
		// This function will be replaced for server-side validation.
		this.registerValidationKeyword("fromSource", function(value, rfAttributes) {
			if (value) {
				if ("true" != rfAttributes["rf.valueFromSource".toLowerCase()]) {
					return "This should be a value from the drop-down list."
				}
			}
		});
		
		// Enable the 'date' validation function
		this.registerValidationKeyword("date", function(value) {
			if (value) {
				var dateRegex = new RegExp(/([0-9]{2})\/([0-9]{2})\/([0-9]{4})/g);
				if (!dateRegex.exec(value)) {
					return "This date should match the format dd/mm/yyyy.";
				}
			}
		});

	}
	
	this.registerValidationKeyword = function(keyword, func) {
		validationKeywords[keyword] = func;
	}
	
	this.registerCustomType = function(keyword, func) {
		customTypes[keyword] = func;
	}
	
	this.loadFlow = function(flowPath, $container, initData, callback) {
		var rf = this;
		$.ajax({
			url: servletUrl,
			cache: false,
			data: {
				"rf.flowPath": flowPath,
				"rf.initData": initData
				},
			success: function(html) {
				insertForm(html, $container);
				if (callback) {
					if (typeof callback === 'function') {
						callback();
					}
				}
			},
			error: function(jqXHR, textStatus, errorThrown) {
				ajaxError("Failed to load form", jqXHR, textStatus, errorThrown);
			}
		})
	}
	
	function ajaxError(message, jqXHR, textStatus, errorThrown) {
		if ("text/plain" == jqXHR.getResponseHeader("Content-Type")) {
			message += ": " + jqXHR.responseText;
		} else {
			message += ": " + errorThrown + ".";
		}
		alert(message);
	}
	
	function insertForm(html, $container) {
		var rf = this;
		
		// Replace container contents with single form
		$container.html(html);
		var $form = $("form", $container);
		
		var flowId = $("[name='rf.flowId']").val();
		
		// Disable standard form submission
		$("form", $container).submit(function() {
			return false;
		})
		
		processIncludeIf($container);
		
		$(":input", $container).on("change", function() {
			processIncludeIf($container);
		});
		
		// Process initialvalue fields
		$("input[rf\\.initialvalue]", $container).each(function() {
			var input = this;
			var $input = $(input);
			if (!$input.val()) {
				var initialvalue = $input.attr("rf.initialvalue");
				var fields = getFieldsMap($form, true);
				var result = null;
				{
					result = eval(initialvalue);
				}
				$input.val(result);
			}
		})
		
		// Process customTypes
		$("input[rf\\.customType]", $container).each(function() {
			var input = this;
			var $input = $(input);
			var customType = $input.attr("rf.customType");
			if (customTypes[customType]) {
				var customTypeFunction = customTypes[customType];
				customTypeFunction(input, flowId);
			} else {
				setupError("Input custom-type not found '" + customType + "'.")
			}
		})
		
		// Wire action buttons
		$form.attr("action", "javascript: void(0)");
		$("[action]", $form).click(function() {
			var action = $(this).attr("action");
			doAction(action, $form, $container);
			return false;
		});
		
		// Give first input focus
		$(":input[type!='hidden'][action!='back']", $container).first().focus();
		
		doOnFormLoad();
	}
	
	this.onFormLoad = function(callback) {
		if (callback instanceof Function) {
			onFormLoadFunctions.push(callback);
		} else {
			throw new TypeError("rhinoforms.onFormLoad() - callback given is not a function.");
		}
	}
	
	function doOnFormLoad() {
		var methodToCall;
		while (methodToCall = onFormLoadFunctions.shift()) {
			methodToCall();
		}
	}
	
	function processIncludeIf($container) {
		var rf = this;
		var $form = $("form", $container);
		var $inputs = $("[rf\\.includeif]", $container);
		rf_trace("processIncludeIf, inputs:" + $inputs.size());
		$inputs.each(function(index) {
			var $input = $(this);
			var includeIfStatement = $input.attr("rf.includeif");
			rf_trace("processIncludeIf index:" + index + ", statement:'" + includeIfStatement + "'");
			var fields = getFieldsMap($form);
			var result = eval(includeIfStatement);
			rf_trace("processIncludeIf result = " + result + "");
			if (result) {
				$input.show();
			} else {
				$input.hide();
			}
		})
	}
	
	function doAction(action, $form, $container) {
		if (action == "back" || action == "cancel" || validateForm($form) == true) {
			var jqXHR = $.ajax({
				url: servletUrl,
				data: $form.serialize() + "&rf.action=" + action,
				type: "POST",
				success: function(data) {
					switch (jqXHR.getResponseHeader("rf.responseType")) {
					case "data":
						$($form.parents()[0]).html($("<h3>").text("Collected Data:").append("<br/>").append($("<textarea>").attr("style", "width: 700px; height: 350px;").text(data)));
						break;
					default:
						insertForm(data, $container);
					}
				},
				error: function(jqXHR, textStatus, errorThrown) {
					ajaxError("Failed to perform action", jqXHR, textStatus, errorThrown);
				}
			});
		} else {
			//alert("Not valid");
		}
	}
	
	function validateForm($form) {
		var submit = true;
		
		// Compile map of fields with their values, include validation options and rf fields
		var fields = getFieldsMap($form, true);
		// Pass map and get list of errors back
		var errors = rf.validateFields(fields);
		
		if (errors.length > 0) {
			// Add invalid class to inputs
			$(":input", $form).removeClass("invalid");
			$(".invalid-message", $form).remove();
			for (var a in errors) {
				var name = errors[a].name;
				var message = errors[a].message;
				var $input = $(":input[name='" + name + "']", $form).addClass("invalid");
				
				// Add error message against input.
				var $errorMessageAfterElement = $input;
				// Only against the last radio element of a set
				if ($input.attr("type") == "radio") {
					$errorMessageAfterElement = $("[name='" + $input.attr("name") + "']", $form).last();
				}
				// Only once for each name
				if ($("[forname='" + name + "']", $form).size() == 0) {
					// After the label if the label is next in the DOM
					var $label = $errorMessageAfterElement.nextAll("label[for='" + $errorMessageAfterElement.attr("id") + "']").first();
					if ($label.size() != 0) {
						$errorMessageAfterElement = $label;
					}

					$errorMessageAfterElement.after($("<span>").addClass("invalid-message").attr("forname", name).text(message));
				}
			}
			
			// Focus first invalid field
			$(":input.invalid", $form).first().focus();
			return false;
		} else {
			// return true if no errors, otherwise false
			return true;
		}
	}
	
	function getFieldsMap($form, includeValidationAndRfAttributes) {
		var fields = {};
		$(":input:visible", $form).each(function() {
			var input = this;
			var $input = $(this);
			var name = $input.attr("name")
			var type = $input.attr("type")
			var value;
			if (type == 'checkbox') {
				value = $input.prop('checked');
			} else if (type == 'radio') {
				if ($input.prop('checked')) {
					value = $input.val();
				} 
			} else {
				value = $input.val();
			}
			
			var validation
			var validationFunction;
			var rfAttributes;
			if (includeValidationAndRfAttributes) {
				validation = $input.attr("rf.validation");
				validationFunction = $input.attr("rf.validationfunction");
				rfAttributes = {};
				for (var a in input.attributes) {
					var attribute = input.attributes[a];
					if (attribute.name && attribute.name.indexOf("rf.") == 0) {
						rfAttributes[attribute.name] = attribute.value;
					}
				}
			}
			
			if (type == 'radio' && fields[name]) {
				if (value) {
					// Update existing radio entry rather than replacing
					fields[name].value = value;
				}
			} else {
				fields[name] = { name:name, value:value, validation:validation, validationFunction:validationFunction, rfAttributes:rfAttributes };
			}
		});
		return fields;
	}
	
	// Take a map of fields and validate each returning a list of any errors.
	// This is also run server-side
	this.validateFields = function(fields) {
		var rf = this;
		var errors = [];
		for (var a in fields) {
			var field = fields[a];
			var error = null;
			if (field.validation) {
				error = validateField(field.name, field.value, field.rfAttributes, field.validation);
			} else if (field.validationFunction) {
				field.validate = function(validationList) {
					error = validateField(this.name, this.value, this.rfAttributes, validationList);
				}
				field.validationFunctionRun = function() {
					eval(field.validationFunction);
				}
				field.validationFunctionRun();
			}
			if (error) {
				errors.push(error);
			}
		}
		return errors;
	}
	
	function validateField(name, value, rfAttributes, validationList) {
		var validationArray = validationList.split(" ");
		for (a in validationArray) {
			var validation = validationArray[a];
			var message = null;
			
			if (validationKeywords[validation]) {
				message = validationKeywords[validation](value, rfAttributes);
				if (message) {
					return { name: name, message: message };
				}
			} else {
				alert("Validation keyword '" + validation + "' is not defined.");
			}
		}
		return null;
	}
	
	function setupError(message) {
		if (rf.alertOnSetupError) alert(message);
		this.trace(message);
	}
	
	this.trace = function(message) {
		rf_trace(message);
	}
	
	this.init();
}

function rf_trace(message) {
	$("#trace").append(message + "\n").parent().scrollTop(9999999);
}
