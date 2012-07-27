{
	main: [
			{ id: "customer", url: "/forms/demo-flow-1/1-customer.html", actions: [ "next" ] },
			{ id: "car", url: "/forms/demo-flow-1/2-car.html", actions: [ "back", "next" ] },
			{ id: "drivers", url: "/forms/demo-flow-1/3-drivers.html", actions: [ "back", "addDriver:driver.addDriver", "next" ] },
			{ id: "quote", url: "/forms/demo-flow-1/5-quote.html", actions: [ "back", "finish" ] }
		],
	driver: [
			{ id: "addDriver", url: "/forms/demo-flow-1/4-add-driver.html", actions: [ "cancel:main.drivers", "next:main.drivers" ] }
		]
			
}