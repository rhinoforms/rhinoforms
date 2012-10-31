package com.rhinoforms.formparser;

import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FieldPathHelper {

	private XPathFactory xPathFactory;
	final Logger logger = LoggerFactory.getLogger(FieldPathHelper.class);

	public FieldPathHelper() {
		this.xPathFactory = XPathFactory.newInstance();
	}
	
	public XPathExpression fieldToXPathExpression(String documentBasePath, String fieldName) throws XPathExpressionException {
		return xPathFactory.newXPath().compile(fieldToXPathString(documentBasePath, fieldName));
	}
	
	public String fieldToXPathString(String documentBasePath, String fieldName) {
		String xPathString = documentBasePath + "/" + fieldName.replaceAll("\\.", "/");
		logger.debug("field name:{}, xPathString:{}", fieldName, xPathString);
		return xPathString;
	}
	
}
