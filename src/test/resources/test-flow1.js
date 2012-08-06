{
	docBase: "/myData",
	formLists: {
		main: [
				{ id: "one", url: "one.html", actions: [ "next" ] },
				{ id: "two", url: "two.html", actions: [ "back", "add:anotherList.editSomething", "next" ] },
				{ id: "three", url: "three.html", actions: [ "back", "add:anotherList.editFish(index=next)", "edit:anotherList.editFish(index=?)", "next" ] },
				{ id: "four", url: "four.html", actions: [ "back", "finish" ] }
		],
		anotherList: [
				{ id: "editSomething", url: "addSomething.html", actions: [ "cancel:main.two", "next:main.two" ] },
				{ id: "editFish", docBase: "/myData/fishes/fish[index]", url: "editFish.html", actions: [ "cancel:main.three", "next:main.editFish" ] }
		]
	}
}