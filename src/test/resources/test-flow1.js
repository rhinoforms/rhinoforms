{
	docBase: "myData",
	formLists: {
		main: [
				{ id: "one", url: "one.html", actions: [ "next" ] },
				{ id: "two", url: "two.html", actions: [ "back", "add:anotherList.addSomething", "next" ] },
				{ id: "three", url: "three.html", actions: [ "back", "finish" ] }
		],
		anotherList: [
				{ id: "addSomething", url: "addSomething.html", actions: [ "cancel:main.two", "next:main.two" ] }
		]
	}
}