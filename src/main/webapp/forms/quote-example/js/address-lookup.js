// Assumes only one address lookup in the document
function AddressLookup() {

	var $findAddressHouse;
	var $findAddressPostcode;
	var $findAddressButton;
	var lookupUrl;
	var inputErrorCallback;

	this.init = function(lookupUrlIn, inputErrorCallbackIn) {
		lookupUrl = lookupUrlIn;
		inputErrorCallback = inputErrorCallbackIn;
		$findAddressHouse = $(".findAddressHouse");
		$findAddressPostcode = $(".findAddressPostcode");
		$findAddressButton = $(".findAddress");

		$(".findAddressButton").click(function() {
			findAddressButtonClick($(this));
		})
		
		$(".changeAddressButton").click(function() {
			changeAddressButtonClick($(this));
		})
		
		if ($("[name='address.line1']").val()) {
			displayMode();
		}
	}

	function findAddressButtonClick($button) {
		var findAddressHouseVal = $findAddressHouse.val();
		var findAddressPostcodeVal = $findAddressPostcode.val();

		if (validPostcode(findAddressPostcodeVal)) {
			$.ajax({
				url : lookupUrl,
				data : {
					requestType : "completeAddress",
					building : findAddressHouseVal,
					postcode : findAddressPostcodeVal
				},
				success : function(data) {
					populateAddress(data);
				},
				failure : function(data) {
					alert("Address lookup failed.");
				}
			});
		} else {
			inputErrorCallback();
		}
	}

	function validPostcode(postcode) {
		// Starting with a letter, containing letters and numbers, at least one
		// number.
		return /^[A-Z][A-Z0-9]*[0-9][A-Z0-9]*$/g.test(postcode
				.replace(/ /g, "").toUpperCase())
	}

	function populateAddress(addressXml) {
		if (addressXml.getElementsByTagName("IsError")[0].childNodes[0].nodeValue == "true") {
			var errorMessage = addressXml.getElementsByTagName("ErrorMessage")[0].childNodes[0].nodeValue;
			alert(errorMessage);
			populateTestAddress();
		} else {
			if ($("InterimResults", addressXml).size() > 0) {
				// Multiple address options
				var $interimResults = $("InterimResult", addressXml);
				if ($interimResults.size() > 0) {
					var $addressListSelect = $(".addressListSelect");
					$addressListSelect.append($("<option>").text("Select your address..."));
					$interimResults.each(function() {
						var id = $("Id", this).text();
						var desc = $("Description", this).text();
						$addressListSelect.append($("<option>").attr("value", id).text(desc));
					})
					$addressListSelect.change(function() {
						addressOptionSelected($(this).val());
					})
					$(".addressList").show();
				} else {
					alert("Sorry, no matches could be found");
				}
			} else {
				if ($("Address", addressXml).size() > 0) {
					addressFound(addressXml);
				} else {
					alert("Sorry, no matches could be found");
				}
			}
		}
	}
	
	function addressOptionSelected(addressId) {
		$.ajax({
			url : lookupUrl,
			data : {
				requestType : "addressLookup",
				addressId : addressId
			},
			success : function(data) {
				addressFound(data);
			},
			failure : function(data) {
				alert("Address lookup failed.");
			}
		});
	}
	
	function addressFound(addressXml) {
		var addressLine1 = "";
		if (addressXml.getElementsByTagName("OrganisationName")[0].childNodes[0]) {
			addressLine1 = addressXml.getElementsByTagName("OrganisationName")[0].childNodes[0].nodeValue + " ";
		}
		if (addressXml.getElementsByTagName("Line1")[0].childNodes[0] != null) {
			addressLine1 = addressLine1 + addressXml.getElementsByTagName("Line1")[0].childNodes[0].nodeValue;
		}
		var addressLine2 = "";
		if (addressXml.getElementsByTagName("Line2")[0].childNodes[0] != null) {
			addressLine2 = addressXml.getElementsByTagName("Line2")[0].childNodes[0].nodeValue;
		}
		var town = "";
		if (addressXml.getElementsByTagName("PostTown")[0].childNodes[0] != null) {
			town = addressXml.getElementsByTagName("PostTown")[0].childNodes[0].nodeValue;
		}
		var county = "";
		if (addressXml.getElementsByTagName("County")[0].childNodes[0] != null) {
			county = addressXml.getElementsByTagName("County")[0].childNodes[0].nodeValue;
		}
		var postcode = addressXml.getElementsByTagName("Postcode")[0].childNodes[0].nodeValue;
		
		setAddress(addressLine1, addressLine2, town, county, postcode);
		$("[name='address.verified']").val("true");
		
		$findAddressHouse.val("");
		$findAddressPostcode.val("");
		
		displayMode();
	}
	
	function setAddress(addressLine1, addressLine2, town, county, postcode) {
		$("[name='address.line1']").val(addressLine1).text(addressLine1);
		$("[name='address.line2']").val(addressLine2).text(addressLine2);
		$("[name='address.line3']").val(town).text(town);
		$("[name='address.line4']").val(county).text(county);
		$("[name='address.postcode']").val(postcode).text(postcode);
	}
	
	function changeAddressButtonClick() {
		setAddress("", "", "", "", "");
		lookupMode();
	}
	
	function lookupMode() {
		$(".findAddress").show();
		$(".addressFound").hide();
	}
	
	function displayMode() {
		$(".addressFound").show();
		$(".findAddress").hide();
	}
	
}
