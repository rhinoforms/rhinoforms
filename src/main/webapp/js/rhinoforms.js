function Rhinoforms() {
	
	var servletUrl = "rhinoforms";
	
	// Should be configurable
	this.alertOnSetupError = true;
	
	// For internal use
	var validationKeywords;
	var customTypes;
	var onFormLoadFunctions = [];
	var onEveryFormLoadFunctions = [];
	
	// For read-only getter
	var currentAction = null;
	
	
	/** Public methods **/
	
	this.init = function() {
		validationKeywords = {};
		customTypes = {};
		
		// Enable the 'required' validation keyword
		this.registerValidationKeyword("required", function(value) {
			if (!value) {
				return "Required value.";
			}
		});

		// Enable the 'email' validation keyword
		this.registerValidationKeyword("email", function(value) {
			if (value) {
				var regex = /^([A-Za-z0-9_\-\.])+\@([A-Za-z0-9_\-\.])+\.([A-Za-z]{2,4})$/;
				if (regex.test(value) != true) {
					return "Invalid email address.";
				}
			}
		});
		
		// Enable the 'fromSource' validation keyword
		// This function will be replaced for server-side validation.
		this.registerValidationKeyword("fromSource", function(value, rfAttributes) {
			if (value) {
				if ("true" != rfAttributes["rf.valueFromSource".toLowerCase()]) {
					return "Not from drop-down list."
				}
			}
		});
		
		// Enable the 'date' validation keyword
		this.registerValidationKeyword("date", function(value, rfAttributes, args) {
			var format = args.format;
			if (value) {
				if (!format) {
					format = "DD/MM/YYYY";
				}
				var formatRegex = new RegExp('^' + format.toLowerCase().replace(/[a-z]/g, '\\d') + '$');
				var parsed = moment(value, format);
				if (!formatRegex.test(value)){
					return "Invalid date. Please use the format " + format.toLowerCase() + ".";
				} else if (parsed == null || !parsed.isValid()) {
					return "Invalid date.";
				}
			}
		});
		
		// Enable the 'pattern' validation keyword
		this.registerValidationKeyword("pattern", function(value, rfAttributes, args) {
			var regex = args.regex;
			var errorMessage = args.errorMessage;
			if (value) {
				if (regex) {
					var re = new RegExp(regex);
					if (!re.test(value)) {
						if (errorMessage) {
							return errorMessage;
						} else {
							return "Does not match the required pattern.";
						}
					}
				} else {
					return "No pattern regex specified."
				}
			}
		});
		
		this.registerValidationKeyword("between", function(value, rfAttributes, args) {
			var decimalA = args.decimalA * 1;
			var decimalB = args.decimalB * 1;
			if (decimalA > decimalB) {
				var decimalC = decimalB;
				decimalB = decimalA;
				decimalA = decimalC;
			}
			value = value * 1;
			if (value < decimalA || value > decimalB) {
				return "Not between " + args.decimalA + " and " + args.decimalB + ".";
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
		var jqXHR = $.ajax({
			url: servletUrl,
			cache: false,
			data: {
				"rf.flowPath": flowPath,
				"rf.initData": initData
				},
			success: function(html) {
				var formId = jqXHR.getResponseHeader("rf.formId");
				insertForm(html, $container, formId);
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
	
	this.onFormLoad = function(callback) {
		if (callback instanceof Function) {
			onFormLoadFunctions.push(callback);
		} else {
			throw new TypeError("rhinoforms.onFormLoad() - callback given is not a function.");
		}
	}
	
	this.onEveryFormLoad = function(callback) {
		if (callback instanceof Function) {
			onEveryFormLoadFunctions.push(callback);
		} else {
			throw new TypeError("rhinoforms.onEveryFormLoad() - callback given is not a function.");
		}
	}
	
	this.getCurrentAction = function() {
		return currentAction;
	}

	/** Private methods **/
	
	function ajaxError(message, jqXHR, textStatus, errorThrown) {
		if ("text/plain" == jqXHR.getResponseHeader("Content-Type")) {
			message += ": " + jqXHR.responseText;
		} else {
			message += ": " + errorThrown + ".";
		}
		alert(message);
	}
	
	function insertForm(html, $container, formId) {
		var rf = this;
		
		// Replace container contents with single form
		$container.html(html);
		var $form = $("form[rhinoforms='true'][parsed='true']", $container).first();
		
		var flowId = $("[name='rf.flowId']").val();
		
		// Disable standard form submission
		$("form", $container).submit(function() {
			return false;
		})
		
		processIncludeIf($container);
		processCalculated($container);
		
		$(":input", $container).on("change", function() {
			processIncludeIf($container);
			processCalculated($container);
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
			var customTypeAttr = $input.attr("rf.customType");
			var customType = toNameAndArgs(customTypeAttr);
			if (customTypes[customType.name]) {
				var customTypeFunction = customTypes[customType.name];
				customTypeFunction(input, flowId, customType.args);
			} else {
				setupError("Input custom-type not found '" + customType.name + "'.")
			}
		});
		
		$("input[rf\\.validateOn='inputBlur']", $container).each(function() {
			var input = this;
			var $input = $(input);
			$input.blur(function() {
				if ($input.val()) {
					validateForm($container, $input.attr("name"));
				}
			});
		});
		
		$("input[rf\\.inputMask]", $container).each(function() {
			var input = this;
			var $input = $(input);
			var mask = $input.attr("rf.inputMask");
			if (mask) {
				var re = new RegExp(mask);
				$input.keypress(function(event) {
					if (0 != event.charCode) {
						var val = $input.val();
						var pos = getCursorPos(input);
						if (pos) {
							var newVal = val.substring(0, pos.start) + String.fromCharCode(event.which) + val.substring(pos.end);
							var result = re.test(newVal);
							return result;
						} else {
							// Not supported
							return true;
						}
					}
				});
			}
		});
		
		// Wire action buttons
		$form.attr("rf.action", "javascript: void(0)");
		$("[rf\\.action]", $form).click(function() {
			var $this = $(this);
			var action = $this.attr("rf.action");
			var type = $this.attr("rf.actionType");
			var container = $this.attr("rf.container");
			var unbind = $this.attr("rf.unbind");
			var suppressDebugBar = false;
			var $actionTargetContainer = $container;
			if (container) {
				$actionTargetContainer = $(container);
				if ($actionTargetContainer.size() > 0 && $container.size() > 0 && $container[0] != $actionTargetContainer[0]) {
					suppressDebugBar = true; // Loading into a different container. We don't want more than one DebugBar.
				}
			}
			doAction(action, type, $form, $actionTargetContainer, { suppressDebugBar: suppressDebugBar, unbind: unbind });
			return false;
		});
		
		$form.addClass("rf-active-form");
		
		// Give first input focus
		var $forFocus = $(":input[action!='back'][type!='hidden']:enabled", $container);
		$forFocus.first().focus();
		
		doOnFormLoad(formId);
	}
	
	function doOnFormLoad(formId) {
		var methodToCall;
		while (methodToCall = onFormLoadFunctions.shift()) {
			methodToCall(formId);
		}
		for (var a = 0; a < onEveryFormLoadFunctions.length; a++) {
			methodToCall = onEveryFormLoadFunctions[a];
			methodToCall(formId);
		}
	}
	
	function processIncludeIf($container) {
		var rf = this;
		var $form = $("form", $container);
		var $inputs = $("[rf\\.includeif]", $container);
		rf_trace("processIncludeIf, inputs:" + $inputs.size());
		if ($inputs.size() > 0) {
			var fields = getFieldsMap($form);
			$inputs.each(function(index) {
				var $input = $(this);
				var includeIfStatement = $input.attr("rf.includeif");
				rf_trace("processIncludeIf index:" + index + ", statement:'" + includeIfStatement + "'");
				var result = eval(includeIfStatement);
				rf_trace("processIncludeIf result = " + result + "");
				
				var inputId = $input.attr("id");
				var $lable;
				if (inputId) {
					$lable = $("[for='" + inputId + "']", $container);
				}
				
				if (result) {
					$input.data("rf.included", true);
					$input.show();
					if ($lable) {
						$lable.show();
					}
				} else {
					$input.data("rf.included", false);
					$input.hide();
					if ($lable) {
						$lable.hide();
					}
					clearInvalid($input, $form);
				}
			})
		}
	}
	
	function processCalculated($container) {
		var rf = this;
		var $form = $("form", $container);
		var $inputs = $("[rf\\.calculated]", $container);
		rf_trace("processCalculated, inputs:" + $inputs.size());
		$inputs.each(function(index) {
			var $input = $(this);
			var calculatedStatement = $input.attr("rf.calculated");
			var fields = getFieldsMap($form);
			rf_trace("processCalculated index:" + index + ", statement:'" + calculatedStatement + "'");
			var result = eval(calculatedStatement);
			rf_trace("processCalculated result = " + result + "");
			$input.val(result).text(result);
		})
	}
	
	function doAction(action, type, $form, $container, options) {
		if (!type) {
			type = action;
		}
		if (type == "back" || type == "cancel" || validateForm($form) == true) {
			var currentAction = action;
			// Deactivate current form
			$form.removeClass("rf-active-form");
			
			if (!(options && options.unbind && options.unbind.toLowerCase() == "false")) {
				$(":input", $form).unbind();
			}

			// Request new form
			var suppressDebugBarString = "";
			if (options && options.suppressDebugBar) {
				suppressDebugBarString = "&rf.suppressDebugBar=true";
			}
			var jqXHR = $.ajax({
				url: servletUrl,
				data: $form.serialize() + "&rf.action=" + action + suppressDebugBarString,
				type: "POST",
				success: function(data) {
					switch (jqXHR.getResponseHeader("rf.responseType")) {
					case "data":
						$($form.parents()[0]).html($("<h3>").text("Collected Data:").append("<br/>").append($("<textarea>").attr("style", "width: 700px; height: 350px;").text(data)));
						break;
					default:
						var formId = jqXHR.getResponseHeader("rf.formId");
						insertForm(data, $container, formId);
					}
				},
				error: function(jqXHR, textStatus, errorThrown) {
					ajaxError("Failed to perform action", jqXHR, textStatus, errorThrown);
				},
				complete: function() {
					currentAction = null;
				}
			});
		}
	}
	
	function validateForm($form, fieldName) {
		var submit = true;

		// Compile map of fields with their values, include validation options and rf fields
		var fields = getFieldsMap($form, true, fieldName);
		// Pass map and get list of errors back
		var errors = rf.validateFields(fields);

		var $inputs = getInputs($form, fieldName);
		
		// Remove invalid messages
		clearInvalid($inputs, $form);
		
		if (errors.length > 0) {
			// Add invalid class to inputs
			for (var a in errors) {
				var name = errors[a].name;
				var message = errors[a].message;
				var $input = $(":input[name='" + name + "']:visible", $form).addClass("invalid");
				
				// Add error message against input.
				var $errorMessageAfterElement = $input;
				// Only against the last radio element of a set
				if ($input.attr("type") == "radio") {
					$errorMessageAfterElement = $("[name='" + $input.attr("name") + "']:visible", $form).last();
				}
				// Only once for each name
				var invalidMessages = $("[forname='" + name + "']", $form);
				if (invalidMessages.not(".invalid-message-cleared").size() == 0) {
					// After the label if the label is next in the DOM
					var $label = $errorMessageAfterElement.nextAll("label[for='" + $errorMessageAfterElement.attr("id") + "']:visible").first();
					if ($label.size() != 0) {
						$errorMessageAfterElement = $label;
					}
					invalidMessages.remove();
					$errorMessageAfterElement.after($("<span>").addClass("invalid-message").attr("forname", name).text(message));
				}
			}
			
			if (!fieldName) {
				// Focus first invalid field
				$(":input.invalid", $form).first().focus();
			}
			return false;
		} else {
			// return true if no errors, otherwise false
			return true;
		}
	}
	
	function clearInvalid($inputs, $form) {
		$inputs.removeClass("invalid");
		var $invalidMessages = $();
		$inputs.each(function() {
			var name = $(this).attr("name");
			if (name) {
				$invalidMessages = $invalidMessages.add(".invalid-message[forname='" + name + "']", $form);
			}
		});
		
		// not removing elements as this can make action button jump up the page before mouseup.
		$invalidMessages.addClass("invalid-message-cleared");
	}
	
	function getFieldsMap($form, includeValidationAndRfAttributes, fieldName) {
		var fields = {};
		var $inputs = getInputs($form, fieldName);

		$inputs.each(function() {
			var input = this;
			var $input = $(this);
			var name = $input.attr("name");
			var type = $input.attr("type");
			var rfIncluded = $input.data("rf.included");
			var included = typeof rfIncluded == "undefined" || rfIncluded == true;
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
				for (var a = 0; a < input.attributes.length; a++) {
					var attribute = input.attributes[a];
					if (attribute.name && attribute.name.indexOf("rf.") == 0) {
						rfAttributes[attribute.name] = attribute.value;
					}
				}
			}
			
			if (name) {
				var mapName = name.replace(/\./g, "_");
				if (type == 'radio' && fields[mapName]) {
					if (value) {
						// Update existing radio entry rather than replacing
						fields[mapName].value = value;
					}
				} else {
					fields[mapName] = { name:name, value:value, validation:validation, validationFunction:validationFunction, rfAttributes:rfAttributes, included:included };
				}
			}
		});
		return fields;
	}
	
	function getInputs($form, fieldName) {
		if (fieldName) {
			return $(":input[name='" + fieldName + "']", $form);
		} else {
			return $(":input", $form);
		}
	}
	
	// Take a map of fields and validate each returning a list of any errors.
	// This is also run server-side
	this.validateFields = function(fields) {
		var rf = this;
		var errors = [];
		for (var a in fields) {
			var field = fields[a];
			if (field.included) {
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
		}
		return errors;
	}
	
	function validateField(name, value, rfAttributes, validationList) {
		var validationArray = toNamesAndArgs(validationList);
		for (a in validationArray) {
			var validation = validationArray[a];
			var message = null;
			if (validationKeywords[validation.name]) {
				message = validationKeywords[validation.name](value, rfAttributes, validation.args);
				if (message) {
					return { name: name, message: message };
				}
			} else {
				alert("Validation keyword '" + validation.name + "' is not defined.");
			}
		}
		return null;
	}

		
	function toNamesAndArgs(string) {
		var namesAndArgs = [];
		var stringSplit = (string + " ").match(/[^ \(]+ |[^ ]+\([^\)]*\) /g);
		for (var a = 0; a < stringSplit.length; a++) {
			var nameArgsString = stringSplit[a];
			namesAndArgs.push(toNameAndArgs(nameArgsString));
		}
		return namesAndArgs;
	}

	function toNameAndArgs(string) {
		string = string.replace(/^\s\s*/, '').replace(/\s\s*$/, '');
		var name;
		var args;
		if (string.indexOf("(") != -1
				&& string.indexOf(")", string.indexOf("(")) != -1) {
			var nameParts = string.split("(");
			name = nameParts[0];
			args = nameParts[1];
			args = args.substring(0, args.length - 1);
			args = eval("(" + args + ")");
		} else {
			name = string;
			args = {};
		}
		return {
			name : name,
			args : args
		};
	}
	
	function setupError(message) {
		if (this.alertOnSetupError) alert(message);
		rf_trace(message);
	}
	
	function getCursorPos(input) {
		if ("selectionStart" in input && document.activeElement == input) {
			return {
				start: input.selectionStart,
				end: input.selectionEnd
			};
		} else if (input.createTextRange) {
			var sel = document.selection.createRange();
			if (sel.parentElement() === input) {
				var rng = input.createTextRange();
				rng.moveToBookmark(sel.getBookmark());
				for (var len = 0; rng.compareEndPoints("EndToStart", rng) > 0; rng.moveEnd("character", -1)) {
					len++;
				}
				rng.setEndPoint("StartToStart", input.createTextRange());
				for (var pos = { start: 0, end: len }; rng.compareEndPoints("EndToStart", rng) > 0; rng.moveEnd("character", -1)) {
					pos.start++;
					pos.end++;
				}
				return pos;
			}
		}
		return null;
	}
	
	this.init();
}

function rf_trace(message) {
	$("#trace").append(message + "\n").parent().scrollTop(9999999);
}

/**
 * Third party libraries
 */

//moment.js
//version : 1.7.0
//author : Tim Wood
//license : MIT
//momentjs.com
(function(a,b){function G(a,b,c){this._d=a,this._isUTC=!!b,this._a=a._a||null,a._a=null,this._lang=c||!1}function H(a){var b=this._data={},c=a.years||a.y||0,d=a.months||a.M||0,e=a.weeks||a.w||0,f=a.days||a.d||0,g=a.hours||a.h||0,h=a.minutes||a.m||0,i=a.seconds||a.s||0,j=a.milliseconds||a.ms||0;this._milliseconds=j+i*1e3+h*6e4+g*36e5,this._days=f+e*7,this._months=d+c*12,b.milliseconds=j%1e3,i+=I(j/1e3),b.seconds=i%60,h+=I(i/60),b.minutes=h%60,g+=I(h/60),b.hours=g%24,f+=I(g/24),f+=e*7,b.days=f%30,d+=I(f/30),b.months=d%12,c+=I(d/12),b.years=c,this._lang=!1}function I(a){return a<0?Math.ceil(a):Math.floor(a)}function J(a,b){var c=a+"";while(c.length<b)c="0"+c;return c}function K(a,b,c){var d=b._milliseconds,e=b._days,f=b._months,g;d&&a._d.setTime(+a+d*c),e&&a.date(a.date()+e*c),f&&(g=a.date(),a.date(1).month(a.month()+f*c).date(Math.min(g,a.daysInMonth())))}function L(a){return Object.prototype.toString.call(a)==="[object Array]"}function M(a,b){var c=Math.min(a.length,b.length),d=Math.abs(a.length-b.length),e=0,f;for(f=0;f<c;f++)~~a[f]!==~~b[f]&&e++;return e+d}function N(b,c){var d,e;for(d=1;d<7;d++)b[d]=b[d]==null?d===2?1:0:b[d];return b[7]=c,e=new a(0),c?(e.setUTCFullYear(b[0],b[1],b[2]),e.setUTCHours(b[3],b[4],b[5],b[6])):(e.setFullYear(b[0],b[1],b[2]),e.setHours(b[3],b[4],b[5],b[6])),e._a=b,e}function O(a,b){var d,e,f=[];!b&&i&&(b=require("./lang/"+a));for(d=0;d<j.length;d++)b[j[d]]=b[j[d]]||g.en[j[d]];for(d=0;d<12;d++)e=c([2e3,d]),f[d]=new RegExp("^"+(b.months[d]||b.months(e,""))+"|^"+(b.monthsShort[d]||b.monthsShort(e,"")).replace(".",""),"i");return b.monthsParse=b.monthsParse||f,g[a]=b,b}function P(a){var b=typeof a=="string"&&a||a&&a._lang||null;return b?g[b]||O(b):c}function Q(a){return D[a]?"'+("+D[a]+")+'":a.replace(n,"").replace(/\\?'/g,"\\'")}function R(a){return P().longDateFormat[a]||a}function S(a){var b="var a,b;return '"+a.replace(l,Q)+"';",c=Function;return new c("t","v","o","p","m",b)}function T(a){return C[a]||(C[a]=S(a)),C[a]}function U(a,b){function d(d,e){return c[d].call?c[d](a,b):c[d][e]}var c=P(a);while(m.test(b))b=b.replace(m,R);return C[b]||(C[b]=S(b)),C[b](a,d,c.ordinal,J,c.meridiem)}function V(a){switch(a){case"DDDD":return r;case"YYYY":return s;case"S":case"SS":case"SSS":case"DDD":return q;case"MMM":case"MMMM":case"dd":case"ddd":case"dddd":case"a":case"A":return t;case"Z":case"ZZ":return u;case"T":return v;case"MM":case"DD":case"YY":case"HH":case"hh":case"mm":case"ss":case"M":case"D":case"d":case"H":case"h":case"m":case"s":return p;default:return new RegExp(a.replace("\\",""))}}function W(a,b,c,d){var e;switch(a){case"M":case"MM":c[1]=b==null?0:~~b-1;break;case"MMM":case"MMMM":for(e=0;e<12;e++)if(P().monthsParse[e].test(b)){c[1]=e;break}break;case"D":case"DD":case"DDD":case"DDDD":b!=null&&(c[2]=~~b);break;case"YY":b=~~b,c[0]=b+(b>70?1900:2e3);break;case"YYYY":c[0]=~~Math.abs(b);break;case"a":case"A":d.isPm=(b+"").toLowerCase()==="pm";break;case"H":case"HH":case"h":case"hh":c[3]=~~b;break;case"m":case"mm":c[4]=~~b;break;case"s":case"ss":c[5]=~~b;break;case"S":case"SS":case"SSS":c[6]=~~(("0."+b)*1e3);break;case"Z":case"ZZ":d.isUTC=!0,e=(b+"").match(z),e&&e[1]&&(d.tzh=~~e[1]),e&&e[2]&&(d.tzm=~~e[2]),e&&e[0]==="+"&&(d.tzh=-d.tzh,d.tzm=-d.tzm)}}function X(a,b){var c=[0,0,1,0,0,0,0],d={tzh:0,tzm:0},e=b.match(l),f,g;for(f=0;f<e.length;f++)g=(V(e[f]).exec(a)||[])[0],a=a.replace(V(e[f]),""),W(e[f],g,c,d);return d.isPm&&c[3]<12&&(c[3]+=12),d.isPm===!1&&c[3]===12&&(c[3]=0),c[3]+=d.tzh,c[4]+=d.tzm,N(c,d.isUTC)}function Y(a,b){var c,d=a.match(o)||[],e,f=99,g,h,i;for(g=0;g<b.length;g++)h=X(a,b[g]),e=U(new G(h),b[g]).match(o)||[],i=M(d,e),i<f&&(f=i,c=h);return c}function Z(b){var c="YYYY-MM-DDT",d;if(w.exec(b)){for(d=0;d<4;d++)if(y[d][1].exec(b)){c+=y[d][0];break}return u.exec(b)?X(b,c+" Z"):X(b,c)}return new a(b)}function $(a,b,c,d,e){var f=e.relativeTime[a];return typeof f=="function"?f(b||1,!!c,a,d):f.replace(/%d/i,b||1)}function _(a,b,c){var d=e(Math.abs(a)/1e3),f=e(d/60),g=e(f/60),h=e(g/24),i=e(h/365),j=d<45&&["s",d]||f===1&&["m"]||f<45&&["mm",f]||g===1&&["h"]||g<22&&["hh",g]||h===1&&["d"]||h<=25&&["dd",h]||h<=45&&["M"]||h<345&&["MM",e(h/30)]||i===1&&["y"]||["yy",i];return j[2]=b,j[3]=a>0,j[4]=c,$.apply({},j)}function ab(a,b){c.fn[a]=function(a){var c=this._isUTC?"UTC":"";return a!=null?(this._d["set"+c+b](a),this):this._d["get"+c+b]()}}function bb(a){c.duration.fn[a]=function(){return this._data[a]}}function cb(a,b){c.duration.fn["as"+a]=function(){return+this/b}}var c,d="1.7.0",e=Math.round,f,g={},h="en",i=typeof module!="undefined"&&module.exports,j="months|monthsShort|weekdays|weekdaysShort|weekdaysMin|longDateFormat|calendar|relativeTime|ordinal|meridiem".split("|"),k=/^\/?Date\((\-?\d+)/i,l=/(\[[^\[]*\])|(\\)?(Mo|MM?M?M?|Do|DDDo|DD?D?D?|ddd?d?|do?|w[o|w]?|YYYY|YY|a|A|hh?|HH?|mm?|ss?|SS?S?|zz?|ZZ?)/g,m=/(LT|LL?L?L?)/g,n=/(^\[)|(\\)|\]$/g,o=/([0-9a-zA-Z\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF]+)/gi,p=/\d\d?/,q=/\d{1,3}/,r=/\d{3}/,s=/\d{1,4}/,t=/[0-9a-z\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF]+/i,u=/Z|[\+\-]\d\d:?\d\d/i,v=/T/i,w=/^\s*\d{4}-\d\d-\d\d(T(\d\d(:\d\d(:\d\d(\.\d\d?\d?)?)?)?)?([\+\-]\d\d:?\d\d)?)?/,x="YYYY-MM-DDTHH:mm:ssZ",y=[["HH:mm:ss.S",/T\d\d:\d\d:\d\d\.\d{1,3}/],["HH:mm:ss",/T\d\d:\d\d:\d\d/],["HH:mm",/T\d\d:\d\d/],["HH",/T\d\d/]],z=/([\+\-]|\d\d)/gi,A="Month|Date|Hours|Minutes|Seconds|Milliseconds".split("|"),B={Milliseconds:1,Seconds:1e3,Minutes:6e4,Hours:36e5,Days:864e5,Months:2592e6,Years:31536e6},C={},D={M:"(a=t.month()+1)",MMM:'v("monthsShort",t.month())',MMMM:'v("months",t.month())',D:"(a=t.date())",DDD:"(a=new Date(t.year(),t.month(),t.date()),b=new Date(t.year(),0,1),a=~~(((a-b)/864e5)+1.5))",d:"(a=t.day())",dd:'v("weekdaysMin",t.day())',ddd:'v("weekdaysShort",t.day())',dddd:'v("weekdays",t.day())',w:"(a=new Date(t.year(),t.month(),t.date()-t.day()+5),b=new Date(a.getFullYear(),0,4),a=~~((a-b)/864e5/7+1.5))",YY:"p(t.year()%100,2)",YYYY:"p(t.year(),4)",a:"m(t.hours(),t.minutes(),!0)",A:"m(t.hours(),t.minutes(),!1)",H:"t.hours()",h:"t.hours()%12||12",m:"t.minutes()",s:"t.seconds()",S:"~~(t.milliseconds()/100)",SS:"p(~~(t.milliseconds()/10),2)",SSS:"p(t.milliseconds(),3)",Z:'((a=-t.zone())<0?((a=-a),"-"):"+")+p(~~(a/60),2)+":"+p(~~a%60,2)',ZZ:'((a=-t.zone())<0?((a=-a),"-"):"+")+p(~~(10*a/6),4)'},E="DDD w M D d".split(" "),F="M D H h m s w".split(" ");while(E.length)f=E.pop(),D[f+"o"]=D[f]+"+o(a)";while(F.length)f=F.pop(),D[f+f]="p("+D[f]+",2)";D.DDDD="p("+D.DDD+",3)",c=function(d,e){if(d===null||d==="")return null;var f,g;return c.isMoment(d)?new G(new a(+d._d),d._isUTC,d._lang):(e?L(e)?f=Y(d,e):f=X(d,e):(g=k.exec(d),f=d===b?new a:g?new a(+g[1]):d instanceof a?d:L(d)?N(d):typeof d=="string"?Z(d):new a(d)),new G(f))},c.utc=function(a,b){return L(a)?new G(N(a,!0),!0):(typeof a=="string"&&!u.exec(a)&&(a+=" +0000",b&&(b+=" Z")),c(a,b).utc())},c.unix=function(a){return c(a*1e3)},c.duration=function(a,b){var d=c.isDuration(a),e=typeof a=="number",f=d?a._data:e?{}:a,g;return e&&(b?f[b]=a:f.milliseconds=a),g=new H(f),d&&(g._lang=a._lang),g},c.humanizeDuration=function(a,b,d){return c.duration(a,b===!0?null:b).humanize(b===!0?!0:d)},c.version=d,c.defaultFormat=x,c.lang=function(a,b){var d;if(!a)return h;(b||!g[a])&&O(a,b);if(g[a]){for(d=0;d<j.length;d++)c[j[d]]=g[a][j[d]];c.monthsParse=g[a].monthsParse,h=a}},c.langData=P,c.isMoment=function(a){return a instanceof G},c.isDuration=function(a){return a instanceof H},c.lang("en",{months:"January_February_March_April_May_June_July_August_September_October_November_December".split("_"),monthsShort:"Jan_Feb_Mar_Apr_May_Jun_Jul_Aug_Sep_Oct_Nov_Dec".split("_"),weekdays:"Sunday_Monday_Tuesday_Wednesday_Thursday_Friday_Saturday".split("_"),weekdaysShort:"Sun_Mon_Tue_Wed_Thu_Fri_Sat".split("_"),weekdaysMin:"Su_Mo_Tu_We_Th_Fr_Sa".split("_"),longDateFormat:{LT:"h:mm A",L:"MM/DD/YYYY",LL:"MMMM D YYYY",LLL:"MMMM D YYYY LT",LLLL:"dddd, MMMM D YYYY LT"},meridiem:function(a,b,c){return a>11?c?"pm":"PM":c?"am":"AM"},calendar:{sameDay:"[Today at] LT",nextDay:"[Tomorrow at] LT",nextWeek:"dddd [at] LT",lastDay:"[Yesterday at] LT",lastWeek:"[last] dddd [at] LT",sameElse:"L"},relativeTime:{future:"in %s",past:"%s ago",s:"a few seconds",m:"a minute",mm:"%d minutes",h:"an hour",hh:"%d hours",d:"a day",dd:"%d days",M:"a month",MM:"%d months",y:"a year",yy:"%d years"},ordinal:function(a){var b=a%10;return~~(a%100/10)===1?"th":b===1?"st":b===2?"nd":b===3?"rd":"th"}}),c.fn=G.prototype={clone:function(){return c(this)},valueOf:function(){return+this._d},unix:function(){return Math.floor(+this._d/1e3)},toString:function(){return this._d.toString()},toDate:function(){return this._d},toArray:function(){var a=this;return[a.year(),a.month(),a.date(),a.hours(),a.minutes(),a.seconds(),a.milliseconds(),!!this._isUTC]},isValid:function(){return this._a?!M(this._a,(this._a[7]?c.utc(this):this).toArray()):!isNaN(this._d.getTime())},utc:function(){return this._isUTC=!0,this},local:function(){return this._isUTC=!1,this},format:function(a){return U(this,a?a:c.defaultFormat)},add:function(a,b){var d=b?c.duration(+b,a):c.duration(a);return K(this,d,1),this},subtract:function(a,b){var d=b?c.duration(+b,a):c.duration(a);return K(this,d,-1),this},diff:function(a,b,d){var f=this._isUTC?c(a).utc():c(a).local(),g=(this.zone()-f.zone())*6e4,h=this._d-f._d-g,i=this.year()-f.year(),j=this.month()-f.month(),k=this.date()-f.date(),l;return b==="months"?l=i*12+j+k/30:b==="years"?l=i+(j+k/30)/12:l=b==="seconds"?h/1e3:b==="minutes"?h/6e4:b==="hours"?h/36e5:b==="days"?h/864e5:b==="weeks"?h/6048e5:h,d?l:e(l)},from:function(a,b){return c.duration(this.diff(a)).lang(this._lang).humanize(!b)},fromNow:function(a){return this.from(c(),a)},calendar:function(){var a=this.diff(c().sod(),"days",!0),b=this.lang().calendar,d=b.sameElse,e=a<-6?d:a<-1?b.lastWeek:a<0?b.lastDay:a<1?b.sameDay:a<2?b.nextDay:a<7?b.nextWeek:d;return this.format(typeof e=="function"?e.apply(this):e)},isLeapYear:function(){var a=this.year();return a%4===0&&a%100!==0||a%400===0},isDST:function(){return this.zone()<c([this.year()]).zone()||this.zone()<c([this.year(),5]).zone()},day:function(a){var b=this._isUTC?this._d.getUTCDay():this._d.getDay();return a==null?b:this.add({d:a-b})},startOf:function(a){switch(a.replace(/s$/,"")){case"year":this.month(0);case"month":this.date(1);case"day":this.hours(0);case"hour":this.minutes(0);case"minute":this.seconds(0);case"second":this.milliseconds(0)}return this},endOf:function(a){return this.startOf(a).add(a.replace(/s?$/,"s"),1).subtract("ms",1)},sod:function(){return this.clone().startOf("day")},eod:function(){return this.clone().endOf("day")},zone:function(){return this._isUTC?0:this._d.getTimezoneOffset()},daysInMonth:function(){return c.utc([this.year(),this.month()+1,0]).date()},lang:function(a){return a===b?P(this):(this._lang=a,this)}};for(f=0;f<A.length;f++)ab(A[f].toLowerCase(),A[f]);ab("year","FullYear"),c.duration.fn=H.prototype={weeks:function(){return I(this.days()/7)},valueOf:function(){return this._milliseconds+this._days*864e5+this._months*2592e6},humanize:function(a){var b=+this,c=this.lang().relativeTime,d=_(b,!a,this.lang());return a&&(d=(b<=0?c.past:c.future).replace(/%s/i,d)),d},lang:c.fn.lang};for(f in B)B.hasOwnProperty(f)&&(cb(f,B[f]),bb(f.toLowerCase()));cb("Weeks",6048e5),i&&(module.exports=c),typeof ender=="undefined"&&(this.moment=c),typeof define=="function"&&define.amd&&define("moment",[],function(){return c})}).call(this,Date);