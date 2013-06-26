{
	docBase: "/myData",
	libraries: ["js/testUtil.js"],
	formLists: {
		main: [
		       	{ id: "zero", url: "zero.html", actions: [ "next" ] },
				{ id: "one", url: "one.html",
					actions: [
						{
							name: "sendToMyServer",
							submission: {
								url: "http://localhost/dummy-url",
								data: {
									type: "10",
									paramA: "[dataDocument]"
								},
								resultInsertPoint: "/myData/submissionResult"
							}
						},
						{ 
							name: "transform", 
							dataDocTransform: "xslt/inlineTransform.xsl"
						},
						{
							/** Named cancel action with specific target */
							name: "cancel-back-to-one",
							type: "cancel",
							target: "one"
						}
					]
				}
		]
	}
}