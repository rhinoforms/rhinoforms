function RhinoformsCreator() {
	
	this.$container = null;
	this.$currentFieldDiv = null;
	this.$fieldEditor = null;
	
	this.init = function(container) {
		this.$container = $(container);
		this.$fieldEditor = $("#fieldEditor");
		
		var rfc = this;
		$("[name='label']", this.$fieldEditor).bind("input paste", function() {
			$("label", rfc.$currentFieldDiv).text($(this).val());
		})
		$("[name='name']", this.$fieldEditor).bind("input paste", function() {
			$("input", rfc.$currentFieldDiv).attr("name", $(this).val());
		})
		$("[name='validation']", this.$fieldEditor).bind("input paste", function() {
			$("input", rfc.$currentFieldDiv).attr("validation", $(this).val());
		})
		$("[name='validationFunction']", this.$fieldEditor).bind("input paste", function() {
			$("input", rfc.$currentFieldDiv).attr("validationFunction", $(this).val());
		})
		
		this.$fieldEditor.hover(function() { rfc.overFieldEditor($(this)); }, function() { rfc.outFieldEditor($(this)); });
	}
	
	this.newForm = function() {
		this.$container.html($("<form>").attr("rhinoforms", "true"));
	}
	
	this.addInput = function() {
		var $fieldDiv = $("<div>").addClass("rfField");
		var label = $("<label>").attr("for", "");
		var input = $("<input>").attr("name", "").attr("type", "text");
		$fieldDiv.append(label).append("<br/>").append(input);
		this.setLabel("New Field", $fieldDiv);
		this.setName("newField", $fieldDiv);
		var rfc = this;
		$("form", this.$container).append($fieldDiv);
		$(".rfField", this.$container).hover(function() { rfc.overField($(this)); }, function() { rfc.outField($(this)); });
	}
	
	this.setName = function(name, fieldDiv) {
		$("label", fieldDiv).attr("for", name);
		$("input", fieldDiv).attr("name", name);
	}

	this.setLabel = function(label, fieldDiv) {
		$("label", fieldDiv).text(label);
	}
	
	this.overField = function($fieldDiv) {
		this.$currentFieldDiv = $fieldDiv;
		this.$currentFieldDiv.overField = true;
		
		$(".rfField", this.$container).removeClass("rfFieldOver");
		$fieldDiv.addClass("rfFieldOver");

		$("[name='label']", this.$fieldEditor).val($("label", this.$currentFieldDiv).text());
		$("[name='name']", this.$fieldEditor).val($("input", this.$currentFieldDiv).attr("name"));
		$("[name='validation']", this.$fieldEditor).val($("input", this.$currentFieldDiv).attr("validation"));
		$("[name='validationFunction']", this.$fieldEditor).val($("input", this.$currentFieldDiv).attr("validationfunction"));
		this.$fieldEditor.show().css("top", $fieldDiv[0].offsetTop);
	}
	
	this.outField = function($fieldDiv) {
		this.$currentFieldDiv.overField = false;
		var rfc = this;
		setTimeout(function() {rfc.checkFieldOut()}, 100);
	}
	
	this.overFieldEditor = function() {
		this.$currentFieldDiv.overFieldEditor = true;
	}

	this.outFieldEditor = function() {
		this.$currentFieldDiv.overFieldEditor = false;
		var rfc = this;
		setTimeout(function() {rfc.checkFieldOut()}, 100);
	}
	
	this.checkFieldOut = function() {
		if (this.$currentFieldDiv != null && this.$currentFieldDiv.overField != true && this.$currentFieldDiv.overFieldEditor != true ) {
			this.$currentFieldDiv.removeClass("rfFieldOver");
			this.$fieldEditor.hide();
			this.$currentFieldDiv = null;
		}
	}
	
}
