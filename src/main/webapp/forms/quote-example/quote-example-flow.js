{
	docBase: "/quote",
	formLists: {
		main: [
			{ id: "customer", docBase: "/quote/customer", url: "/forms/quote-example/1-customer.html", actions: [ "next" ] },
			{ id: "driver", docBase: "/quote/customer", url: "/forms/quote-example/2-driver.html",
				actions: [
					"back",
					
					"addConviction:conviction.editConviction(index=next)",
					"editConviction:conviction.editConviction(index=?)",
					"deleteConviction:_delete(xpath=convictions/conviction[index], index=?)",
					
					"addClaim:claim.editClaim(index=next)",
					"editClaim:claim.editClaim(index=?)",
					"deleteClaim:_delete(xpath=claims/claim[index], index=?)",
					
					"next"
				]
			},
			{ id: "drivers", url: "/forms/quote-example/5-drivers.html", 
				actions: [
					"back", 

					"addAdditionalDriver:additionalDriver.editAdditionalDriver(index=next)",
					"editAdditionalDriver:additionalDriver.editAdditionalDriver(index=?)",
					"deleteAdditionalDriver:_delete(xpath=additionalDrivers/driver[index], index=?)",

					"next"
				]
			},
			{ id: "quote", url: "/forms/quote-example/7-quote.html", actions: [ "back", "finish" ] }
		],
		conviction: [
			{ id: "editConviction", docBase: "convictions/conviction[index]", url: "/forms/quote-example/3-edit-conviction.html", actions: [ "cancel", "next" ] }
		],
		claim: [
			{ id: "editClaim", docBase: "claims/claim[index]", url: "/forms/quote-example/4-edit-claim.html", actions: [ "cancel", "next" ] }
		],
		additionalDriver: [
			{ id: "editAdditionalDriver", docBase: "/quote/additionalDrivers/driver[index]", url: "/forms/quote-example/6-edit-additional-driver.html", 
				actions: [
					"back",
					
					"addConviction:conviction.editConviction(index=next)",
					"editConviction:conviction.editConviction(index=?)",
					"deleteConviction:_delete(xpath=convictions/conviction[index], index=?)",
					
					"addClaim:claim.editClaim(index=next)",
					"editClaim:claim.editClaim(index=?)",
					"deleteClaim:_delete(xpath=claims/claim[index], index=?)",
					
					"next"
				]
			},
		]
	}
}