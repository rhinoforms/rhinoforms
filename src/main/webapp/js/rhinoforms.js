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
		this.registerValidationKeyword("required", function(name, value) {
			if (!value) {
				return name + " is required.";
			}
		});

		// Enable the 'email' validation function
		this.registerValidationKeyword("email", function(name, value) {
			if (value) {
				var regex = /^([A-Za-z0-9_\-\.])+\@([A-Za-z0-9_\-\.])+\.([A-Za-z]{2,4})$/;
				if (regex.test(value) != true) {
					return name + " is not a valid email address.";
				}
			}
		});
		
		// Enable the 'fromSource' validation keyword
		// This function will be replaced for server-side validation.
		this.registerValidationKeyword("fromSource", function(name, value, rfAttributes) {
			if (value) {
				if ("true" != rfAttributes["rf.valueFromSource".toLowerCase()]) {
					return name + " should be a value from the drop-down list."
				}
			}
		});
		
		// Enable the 'date' validation function
		this.registerValidationKeyword("date", function(name, value) {
			if (value) {
				var dateRegex = new RegExp(/([0-9]{2})\/([0-9]{2})\/([0-9]{4})/g);
				if (!dateRegex.exec(value)) {
					return name + " should match the format dd/mm/yyyy, got '" + value + "', regex '" + dateRegex + "'";
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
		$("input[type!='hidden']", $container).first().focus();
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
				error: function() {
					alert("Failed to perform action.");
				}
			});
		} else {
			//alert("Not valid");
		}
	}
	
	this.validateForm = function($form) {
		var simpleForm = this;
		var submit = true;
		
		// Compile map of fields with their values and validation options
		var fields = {};
		$(":input", $form).each(function() {
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
			var validation = $input.attr("rf.validation");
			var validationFunction = $input.attr("rf.validationfunction");
			var rfAttributes = {};
			for (var a in input.attributes) {
				var attribute = input.attributes[a];
				if (attribute.name && attribute.name.indexOf("rf.") == 0) {
					rfAttributes[attribute.name] = attribute.value;
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
		
		// Pass map and get list of errors back
		var errors = this.validateFields(fields);
		
		// Build error list
		var $errorList = $("<ul>").addClass("rfError");
		for (var a in errors) {
			$errorList.append($("<li>").attr("name", errors[a].name).html(errors[a].message));
		}
		
		if (errors.length > 0) {
			// Attach error list to dom
			var prev = $form.prev();
			if (prev.hasClass("rfError")) {
				prev.html($errorList.html());
			} else {
				$form.before($errorList);
			}
			
			// Add invalid class to inputs
			$("input", $form).removeClass("invalid");
			for (var a in errors) {
				$("input[name='" + errors[a].name + "']", $form).addClass("invalid");
			}
			
			// Focus first invalid field
			$("input.invalid", $form).first().focus();
			return false;
		} else {
			// return true if no errors, otherwise false
			return true;
		}
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
				message = this.validationKeywords[validation](name, value, rfAttributes);
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
