{
	docBase: "/customer",
	formLists: {
		main: [
				{ id: "customer", url: "/forms/quote-example/1-customer.html", actions: [ "next" ] },
				{ id: "car", url: "/forms/quote-example/2-car.html", actions: [ "back", "next" ] },
				{ id: "drivers", url: "/forms/quote-example/3-drivers.html", 
					actions: [
					          "back", 
					          "addDriver:driver.editDriver(index=next)", 
					          "editDriver:driver.editDriver(index=?)",
					          "deleteDriver:_delete(xpath=/customer/drivers/driver[index], index=?)",
					          "next"
					          ] },
				{ id: "quote", url: "/forms/quote-example/5-quote.html", actions: [ "back", "finish" ] }
			],
		driver: [
				{ id: "editDriver", docBase: "/customer/drivers/driver[index]", url: "/forms/quote-example/4-edit-driver.html", actions: [ "cancel:main.drivers", "next:main.drivers" ] }
			]
	}
}