package com.rhinoforms.js;

import java.util.List;
import java.util.Map;

import com.rhinoforms.Constants;
import com.rhinoforms.serverside.InputPojo;

public class JSSerialiser {

	public String inputPOJOListToJS(List<InputPojo> inputPojos) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("{");
		boolean first = true;
		for (InputPojo inputPOJO : inputPojos) {
			// { name:"name", value:"value", validation:"validation",
			// validationFunction:"validationFunction",
			// rfAttributes:{ rf.source:"http://somewhere/something" } };

			String name = inputPOJO.getName();
			if (name != null && !name.isEmpty()) {
				if (first) {
					first = false;
				} else {
					stringBuilder.append(",");
				}
				stringBuilder.append("\"");
				stringBuilder.append(name.replaceAll("\\.", "_"));
				stringBuilder.append("\":");
				stringBuilder.append("{");
				stringBuilder.append("name:\"");
				stringBuilder.append(inputPOJO.getName());
				stringBuilder.append("\",");
				stringBuilder.append("value:");
				if (inputPOJO.getType().equalsIgnoreCase("checkbox")) {
					stringBuilder.append(inputPOJO.getValue());
				} else {
					stringBuilder.append("\"");
					stringBuilder.append(inputPOJO.getValue());
					stringBuilder.append("\"");
				}
				if (inputPOJO.getValidation() != null) {
					stringBuilder.append(",validation:\"");
					stringBuilder.append(inputPOJO.getValidation());
					stringBuilder.append("\"");
				}
				if (inputPOJO.getValidationFunction() != null) {
					stringBuilder.append(",validationFunction:\"");
					stringBuilder.append(inputPOJO.getValidationFunction().replaceAll("\"", "'"));
					stringBuilder.append("\"");
				}
				Map<String, String> rfAttributes = inputPOJO.getRfAttributes();
				if (rfAttributes != null) {
					stringBuilder.append(",rfAttributes:{");
					boolean firstAttr = true;
					for (String rfAttributeKey : rfAttributes.keySet()) {
						if (!rfAttributeKey.equals(Constants.VALIDATION_ATTR) && !rfAttributeKey.equals(Constants.VALIDATION_FUNCTION_ATTR)) {
							if (firstAttr) {
								firstAttr = false;
							} else {
								stringBuilder.append(",");
							}
							stringBuilder.append("\"").append(rfAttributeKey).append("\":");
							stringBuilder.append("\"").append(rfAttributes.get(rfAttributeKey)).append("\"");
						}
					}
					stringBuilder.append("}");
				}
				stringBuilder.append(",included:true");
				stringBuilder.append("}");
			}
		}
		stringBuilder.append("}");
		return stringBuilder.toString();
	}

}
