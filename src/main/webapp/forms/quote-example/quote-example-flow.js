{
	docBase: "/quote",
	formLists: {
		main: [
			{ id: "customer", docBase: "/quote/customer", url: "/forms/quote-example/1-customer.html", actions: [ "next" ] },
			{ id: "driver", url: "/forms/quote-example/2-driver.html",
				actions: [
					"back", 
					"addConviction:conviction.editConviction(index=next)", 
					"editConviction:conviction.editConviction(index=?)",
					"deleteConviction:_delete(xpath=/quote/convictions/conviction[index], index=?)",
					"next"
				] },
			{ id: "quote", url: "/forms/quote-example/5-quote.html", actions: [ "back", "finish" ] }
		],
		conviction: [
			{ id: "editConviction", docBase: "/quote/convictions/conviction[index]", url: "/forms/quote-example/3-edit-conviction.html", actions: [ "cancel:main.driver", "next:main.driver" ] }
		]
	}
}