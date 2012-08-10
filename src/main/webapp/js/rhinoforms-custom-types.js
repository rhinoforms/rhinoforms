rf.registerCustomType("auto-complete-select", function(inputElement, flowId) {
	
	this.$input = $(inputElement);
	this.flowId = flowId;
	
	this.name;
	this.value;
	this.lastLookup;
	this.lookupLoading;
	this.$list;
	
	this.init = function() {
		var ct = this;
		this.name = this.$input.attr("name");
		this.value = this.$input.attr("value");
		this.source = this.$input.attr("rf.source");
		this.$list = $("<div>").addClass("rf-dropdown");
		
		$input.on("input textInput propertychange paste cut keydown drop", function() {
			var value = $(this).attr("value");
			if (ct.value != value) {
				ct.valueChanged(value);
			}
		});
		$input.on("focus", function() {
			ct.showList();
		})
		$input.on("blur", function() {
			ct.hideList();
		})
//		$input.on("keydown", function(e) {
//			if (e.keyCode == 40) {
//				alert("down");
//			} else if (e.keyCode == 38) {
//				alert("up");
//			}
//		})
	}
	
	this.valueChanged = function(value) {
		this.value = value;
//		alert("Value changed '" + this.value + "'");
		if (value.length >= 3) {
			var lookup = value.substring(0, 3);
			lookup = firstUpper(lookup);
			if (lookup != this.lastLookup) {
				this.doLookup(lookup);
			} else {
				this.showList();
				this.filter(value);
			}
		} else {
			this.hideList();
		}
		
	}
	
	this.doLookup = function(lookup) {
		this.lookupLoading = lookup;
//		alert("Doing lookup '" + lookup + "'");

		this.$list.detach();
		
		var ct = this;
		$.ajax({
			url: source,
			data: {"value": lookup, "rf.flowId": ct.flowId},
			success: function(data) {
				var $ul = $("<ul>");
				ct.$list = $("<div>").addClass("rf-dropdown").append($ul);
				if (lookup == ct.lookupLoading) {
//					alert("Lookup success " + data.length);
					ct.lastLookup = lookup;
					
					for (var i in data) {
						var item = data[i];
						var $li = $("<li>").attr("val", item[0]).attr("text", item[1]).text(item[1]);
						$ul.append($li);
					}
					
					$("li", $ul).click(function() {
						alert($(this).attr("text"));
					})
					ct.filter(lookup);
					ct.$list.css("left", ct.$input[0].offsetLeft);
					ct.$input.after(ct.$list);
				} else {
					// discard old lookup
				}
			},
			error: function() {
				if (lookup == ct.lookupLoading) {
					alert("Lookup failed");
				}
			}
		});
	}
	
	this.firstUpper = function(string) {
		return string.substring(0, 1).toUpperCase() + string.substring(1).toLowerCase();
	}
	
	this.filter = function(value) {
		var valueLow = value.toLowerCase();
		$("li", this.$list).each(function() {
			var $li = $(this);
			var text = $li.attr("text");
			if (text.toLowerCase().indexOf(valueLow) == 0) {
				$li.html("<b>" + text.substring(0, value.length) + "</b>" + text.substring(value.length));
				$li.show();
			} else {
				$li.hide();
			}
		});
	}
	
	this.showList = function() {
		this.$list.show();
	}
	
	this.hideList = function() {
		this.$list.hide();
	}
	
	this.init();
	
});