{
	docBase: "/myData",
	formLists: {
		main: [
				{ id: "one", url: "/forms/sleep/1-sleep.html", 
					actions: [
						{
							name: "next",
							submission:
								{
									url: "http://localhost:8080/sleep/5",
									method: "post"
								}
						}
					]
				},
				{ id: "two", url: "/forms/sleep/2-sleep.html", actions: ["back"] }
			]
	}
}