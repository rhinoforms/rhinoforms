rf.registerCustomType("auto-complete-select", function(inputElement, flowId) {
	
	this.$input = $(inputElement);
	this.flowId = flowId;
	
	this.name;
	this.value;
	this.lastLookup;
	this.lookupLoading;
	this.$list;
	this.$ul;
	this.mouseDown = false;
	
	this.init = function() {
		var ct = this;
		this.name = this.$input.attr("name");
		this.value = this.$input.attr("value");
		this.source = this.$input.attr("rf.source");
		this.$list = $("<div>").addClass("rf-dropdown");
		
		this.$input.on("input textInput propertychange paste cut keydown drop focus", function() {
			var value = $(this).attr("value");
			if (ct.value != value) {
				ct.valueChanged(value);
			}
		});

		this.$input.on("blur", function() {
			if (!ct.mouseDown) {
				ct.hideList();
			}
		})
		
		// Navigate list with arrow keys
		this.$input.on("keydown", function(e) {
			var keyCode = e.keyCode;
			if ((keyCode == 40 || keyCode == 38) && $("li", ct.$list).size() > 0) {
				var $item = ct.getOverItem();
				if (keyCode == 40) {
					$item = $item.nextAll(":visible").first();
					if ($item.size() == 0) {
						$item = $("li:visible", ct.$list).first();
					}
				} else if (keyCode == 38) {
					$item = $item.prevAll(":visible").first();
					if ($item.size() == 0) {
						$item = $("li:visible", ct.$list).last();
					}
				}
				ct.over($item);
				scroll($item);
			}
		})
		
		// Select list item with enter button or tab
		this.$input.on("keydown", function(e) {
			var keyCode = e.keyCode;
			if (keyCode == 13 || keyCode == 9) {
				var $item = ct.getOverItem();
				if ($item.size() != 0) {
					ct.selectItem($item)
				}
			}
		})
	}
	
	this.valueChanged = function(value) {
		this.value = value;
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
		this.$list.detach();
		
		var ct = this;
		$.ajax({
			url: source,
			data: {"value": lookup, "rf.flowId": ct.flowId},
			success: function(data) {
				if (lookup == ct.lookupLoading) {
					var $ul = $("<ul>");
					ct.$list = $("<div>").addClass("rf-dropdown").append($ul);
					ct.lastLookup = lookup;
					
					for (var i in data) {
						var item = data[i];
						var $li = $("<li>").attr("val", item[0]).attr("text", item[1]).text(item[1]);
						$ul.append($li);
					}
					
					ct.filter(lookup);
					ct.$list.css("left", ct.$input[0].offsetLeft);
					ct.$input.after(ct.$list);
					ct.$ul = $("ul", $list);
					$("li", $list).on("mouseover", function () {
						ct.over($(this));
					}).on("mousedown", function () {
						ct.mouseDown = true;
					}).on("mouseout", function () {
						if (ct.mouseDown) {
							ct.mouseDown = false;
							ct.hideList();
						}
					}).on("click", function () {
						ct.mouseDown = false;
						ct.selectItem($(this));
					})
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
			var textLower = text.toLowerCase();
			var index = textLower.indexOf(valueLow);
			if (index == -1) {
				index = textLower.indexOf(" " + valueLow);
			}
			if (index != -1) {
				$li.html(text.substring(0, index) +  "<b>" + text.substring(index, index + value.length) + "</b>" + text.substring(index + value.length));
				$li.show();
			} else {
				$li.hide();
			}
		});
	}
	
	this.showList = function() {
		this.overNone();
		this.$list.show();
	}
	
	this.hideList = function() {
		this.$list.hide();
	}
	
	this.overNone = function() {
		$("li", $list).removeClass("over");
	}
	
	this.over = function($item) {
		this.overNone();
		$item.addClass("over");
	}
	
	this.scroll = function($item) {
		this.$ul.scrollTop($item.offset().top - this.$ul.offset().top + this.$ul.scrollTop());
	}
	
	this.getOverItem = function() {
		return $("li.over", this.$list);
	}
	
	this.selectItem = function($item) {
		var text = $item.text();
		this.value = text;
		this.$input.val(text);
		this.filter(text);
		this.hideList();
	}
	
	this.init();
	
});