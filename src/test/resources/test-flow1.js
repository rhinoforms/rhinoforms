{
	docBase: "/myData",
	libraries: ["js/testUtil.js"],
	formLists: {
		main: [
				{ id: "one", url: "one.html", actions: [ "next" ] },
				{ id: "two", url: "two.html", actions: [ "back", "add:anotherList.editFish(fishIndex=next)", "edit:anotherList.editFish(fishIndex=?)", "next" ] },
				{ id: "three", url: "three.html",
					actions: [
						"back",
						"finish",
						{
							name: "sendToMyServer",
							submission: {
								preTransform: "xslt/toServerFormat.xsl",
								url: "http://localhost/dummy-url",
								method: "post",
								data: {
									type: "10",
									paramA: "[dataDocument]"
								},
								postTransform: "xslt/fromServerFormat.xsl",
								resultInsertPoint: "/myData/submissionResult"
							}
						}
					]
				}
		],
		anotherList: [
			{ id: "editFish", docBase: "fishes/fish[fishIndex]", url: "editFish.html", actions: [ "cancel", "addGill:gills.editGill(gillIndex=next)", "next" ] }
		],
		gills: [
			{ id: "editGill", docBase: "gills/gill[gillIndex]", url: "editGill.html", actions: [ "cancel", "next" ] }
		]
	}
}