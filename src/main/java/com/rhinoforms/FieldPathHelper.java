package com.rhinoforms;

import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.log4j.Logger;

public class FieldPathHelper {

	private XPathFactory xPathFactory;
	private static final Logger LOGGER = Logger.getLogger(FieldPathHelper.class);

	public FieldPathHelper() {
		this.xPathFactory = XPathFactory.newInstance();
	}
	
	public XPathExpression fieldToXPathExpression(String documentBasePath, String fieldName) throws XPathExpressionException {
		return xPathFactory.newXPath().compile(fieldToXPathString(documentBasePath, fieldName));
	}
	
	public String fieldToXPathString(String documentBasePath, String fieldName) {
		String xPathString = documentBasePath + "/" + fieldName.replaceAll("\\.", "/");
		LOGGER.debug("field name:" + fieldName + ", xPathString:" + xPathString);
		return xPathString;
	}
	
}
