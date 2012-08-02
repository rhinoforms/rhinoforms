function Rhinoforms() {
	
	this.loadFlow = function(flowPath, $container, initData) {
		var rf = this;
		$.ajax({
			url: "form",
			data: {
				"rf.flowPath": flowPath,
				"rf.initData": initData
				},
			success: function(html) {
				rf.insertForm(html, $container);
			},
			failure: function() {
				alert("Failed to load form");
			}
		})
	}
	
	this.insertForm = function(html, $container) {
		$container.html(html);
		$("form", $container).submit(function() {
			return false;
		})
		$("form", $container).each(function() {
			var $form = $(this);
			$form.attr("action", "javascript: void(0)");
			$("button[action]", $form).click(function() {
				var action = $(this).attr("action");
				rf.doAction(action, $form, $container);
				return false;
			});
		});
	}
	
	this.doAction = function(action, $form, $container) {
		var rf = this;
		if (action == "back" || this.validateForm($form) == true) {
			var jqXHR = $.ajax({
				url: "form",
				data: $form.serialize() + "&rf.action=" + action,
				type: "POST",
				success: function(data) {
					switch (jqXHR.getResponseHeader("rf.responseType")) {
					case "data":
						$($form.parents()[0]).html($("<textarea>").attr("style", "width: 700px; height: 350px;").text(data));
						break;
					default:
						rf.insertForm(data, $container);
					}
				},
				failure: function() {
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
			var $input = $(this);
			var name = $input.attr("name")
			var type = $input.attr("type")
			var value;
			if (type == 'checkbox') {
				value = $input.prop('checked');
			} else {
				value = $input.val();
			}
			var validation = $input.attr('validation');
			var validationFunction = $input.attr('validationFunction');
			
			fields[name] = { name:name, value:value, validation:validation, validationFunction:validationFunction };
		});
		
		// Pass map and get list of errors back
		var errors = this.validateFields(fields);
		
		// Build error list
		var $errorList = $("<ul>").addClass("rfError");
		for (var a in errors) {
			$errorList.append($("<li>").attr("name", errors[a].name).html(errors[a].message));
		}
		
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
		
		// return true if no errors, otherwise false
		return errors.length == 0;
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
				error = this.validateField(field.name, field.value, field.validation);
			} else if (field.validationFunction) {
				field.validate = function(validationList) {
					error = simpleForm.validateField(this.name, this.value, validationList);
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
	
	this.validateField = function(name, value, validationList) {
		var validationArray = validationList.split(" ");
		for (a in validationArray) {
			var validation = validationArray[a];
			var message = null;
			if (validation == "required") {
				if (value.length == 0) {
					message = name + " is required.";
				}
			} else if (validation == "email") {
				var result = this.validateEmail(value);
				if (result != true) {
					message = name + " is not a valid email address.";
				}
			}
			if (message) {
				return { name: name, message: message };
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
	
	this.trace = function(message) {
		$("#trace").append(message);
	}
}