function SimpleForm() {
	
	this.loadForm = function(formPath, $container) {
		$container.html("Loading " + formPath);
		
		$.ajax({
			url: formPath,
			data: { simpleform: "true", random: Math.random() },
			success: function(html) {
				$container.html(html);
			},
			failure: function() {
				alert("Failed to load form");
			}
		})
		
	}
	
	this.validateForm = function(form) {
		var simpleForm = this;
		var submit = true;
		
		// Compile map of fields with their values and validation options
		var fields = {};
		$(":input", form).each(function() {
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
		var $errorList = $("<ul>").addClass("sfError");
		for (var a in errors) {
			$errorList.append($("<li>").html(errors[a]));
		}
		
		// Attach error list to dom
		var $form = $(form);
		var prev = $form.prev();
		if (prev.hasClass("sfError")) {
			prev.html($errorList.html());
		} else {
			$form.before($errorList);
		}
		
		// return true if no errors, otherwise false
		return errors.length == 0;
	}
	
	// Take a map of fields and validate each returning a list of any errors
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
			if (validation == "required") {
				if (value.length == 0) {
					return name + " is required.";
				}
			} else if (validation == "email") {
				var result = this.validateEmail(value);
				if (result != true) {
					return name + " is not a valid email address.";
				}
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
}
