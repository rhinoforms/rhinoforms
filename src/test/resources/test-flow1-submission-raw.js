{
	docBase: "/myData",
	libraries: ["js/testUtil.js"],
	formLists: {
		main: [
				{ id: "one", url: "one.html",
					actions: [
						{
							name: "sendToMyServer",
							submission: {
								url: "http://localhost/dummy-url",
								rawXmlRequest: "true",
								resultInsertPoint: "/myData/submissionResult"
							}
						}
					]
				}
		]
	}
}