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
								url: "{{$my-service}}/REST/{{//calcRef}}",
								data: {
									type: "10",
									paramA: "[dataDocument]"
								},
								resultInsertPoint: "/myData/submissionResult"
							}
						}
					]
				}
		]
	}
}