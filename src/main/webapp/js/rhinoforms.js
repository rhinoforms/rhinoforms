function Rhinoforms() {
	
	// Should be configurable
	this.alertOnSetupError = true;
	
	// For internal use
	this.validationKeywords;
	this.customTypes;
	
	this.init = function() {
		this.validationKeywords = {};
		this.customTypes = {};
		
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
		this.validationKeywords[keyword] = func;
	}
	
	this.registerCustomType = function(keyword, func) {
		this.customTypes[keyword] = func;
	}
	
	this.loadFlow = function(flowPath, $container, initData, callback) {
		var rf = this;
		$.ajax({
			url: "form",
			cache: false,
			data: {
				"rf.flowPath": flowPath,
				"rf.initData": initData
				},
			success: function(html) {
				rf.insertForm(html, $container);
				if (callback) {
					if (typeof callback === 'function') {
						callback();
					}
				}
			},
			failure: function() {
				alert("Failed to load form");
			}
		})
	}
	
	this.insertForm = function(html, $container) {
		var rf = this;
		
		// Insert html
		$container.html(html);
		
		this.flowId = $("[name='rf.flowId']").val();
		
		// Disable standard form submission
		$("form", $container).submit(function() {
			return false;
		})
		
		this.processIncludeIf($container);
		
		$(":input", $container).on("change", function() {
			rf.processIncludeIf($container);
		});
		
		// Process customTypes
		$("input[rf\\.customType]", $container).each(function() {
			var input = this;
			var $input = $(input);
			var customType = $input.attr("rf.customType");
			if (rf.customTypes[customType]) {
				var customTypeFunction = rf.customTypes[customType];
				customTypeFunction(input, rf.flowId);
			} else {
				rf.setupError("Input custom-type not found '" + customType + "'.")
			}
		})
		
		// Wire action buttons
		$("form", $container).each(function() {
			var $form = $(this);
			
			$form.attr("action", "javascript: void(0)");
			$("[action]", $form).click(function() {
				var action = $(this).attr("action");
				rf.doAction(action, $form, $container);
				return false;
			});
		});
		
		// Give first input focus
		$("input[type!='hidden'][action!='back']", $container).first().focus();
	}
	
	this.processIncludeIf = function($container) {
		var rf = this;
		var $form = $("form", $container);
		var $inputs = $("[rf\\.includeif]", $container);
		rf_trace("processIncludeIf, inputs:" + $inputs.size());
		$inputs.each(function(index) {
			var $input = $(this);
			var includeIfStatement = $input.attr("rf.includeif");
			rf_trace("processIncludeIf index:" + index + ", statement:'" + includeIfStatement + "'");
			var fields = rf.getFieldsMap($form);
			var result = eval(includeIfStatement);
			rf_trace("processIncludeIf result = " + result + "");
			if (result) {
				$input.show();
			} else {
				$input.hide();
			}
		})
	}
	
	this.doAction = function(action, $form, $container) {
		var rf = this;
		if (action == "back" || action == "cancel" || this.validateForm($form) == true) {
			var jqXHR = $.ajax({
				url: "form",
				data: $form.serialize() + "&rf.action=" + action,
				type: "POST",
				success: function(data) {
					switch (jqXHR.getResponseHeader("rf.responseType")) {
					case "data":
						$($form.parents()[0]).html($("<h3>").text("Collected Data:").append("<br/>").append($("<textarea>").attr("style", "width: 700px; height: 350px;").text(data)));
						break;
					default:
						rf.insertForm(data, $container);
					}
				},
				error: function(jqXHR, textStatus, errorThrown) {
					alert("Failed to perform action. " + jqXHR.responseText);
				}
			});
		} else {
			//alert("Not valid");
		}
	}
	
	this.validateForm = function($form) {
		var simpleForm = this;
		var submit = true;
		
		// Compile map of fields with their values, include validation options and rf fields
		var fields = this.getFieldsMap($form, true);
		// Pass map and get list of errors back
		var errors = this.validateFields(fields);
		
		if (errors.length > 0) {
			// Add invalid class to inputs
			$("input", $form).removeClass("invalid");
			$(".invalid-message", $form).remove();
			for (var a in errors) {
				var name = errors[a].name;
				var message = errors[a].message;
				$("input[name='" + name + "']", $form).addClass("invalid").after($("<span>").addClass("invalid-message").text(message));
			}
			
			// Focus first invalid field
			$("input.invalid", $form).first().focus();
			return false;
		} else {
			// return true if no errors, otherwise false
			return true;
		}
	}
	
	this.getFieldsMap = function($form, includeValidationAndRfAttributes) {
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
		var simpleForm = this;
		var errors = [];
		for (var a in fields) {
			var field = fields[a];
			var error = null;
			if (field.validation) {
				error = this.validateField(field.name, field.value, field.rfAttributes, field.validation);
			} else if (field.validationFunction) {
				field.validate = function(validationList) {
					error = simpleForm.validateField(this.name, this.value, this.rfAttributes, validationList);
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
	
	this.validateField = function(name, value, rfAttributes, validationList) {
		var validationArray = validationList.split(" ");
		for (a in validationArray) {
			var validation = validationArray[a];
			var message = null;
			
			if (this.validationKeywords[validation]) {
				message = this.validationKeywords[validation](value, rfAttributes);
				if (message) {
					return { name: name, message: message };
				}
			} else {
				alert("Validation keyword '" + validation + "' is not defined.");
			}
		}
		return null;
	}
	
	this.isString = function(something) {
		return true;
	}
	
	this.matches = function(actual, expected) {
		return actual == expected;
	}
	
	this.validateEmail = function(email) {
		var reg = /^([A-Za-z0-9_\-\.])+\@([A-Za-z0-9_\-\.])+\.([A-Za-z]{2,4})$/;
		return reg.test(email);
	}
	
	this.setupError = function(message) {
		if (this.alertOnSetupError) alert(message);
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
