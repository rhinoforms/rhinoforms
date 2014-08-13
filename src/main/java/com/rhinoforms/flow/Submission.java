package com.rhinoforms.flow;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class Submission implements Serializable {

	private String url;
	private String method;
	private Map<String, String> data;
	private String resultInsertPoint;
	private String preTransform;
	private String postTransform;
	private String messageOnHttpError;
	private boolean omitXmlDeclaration;
	private boolean rawXmlRequest;
	private boolean jsonToXml;
	private boolean jsonToXmlTypeHints;
	private String jsonToXmlRootName;
	private static final long serialVersionUID = -6314856649818697445L;

	public Submission(String url) {
		this.url = url;
		data = new LinkedHashMap<>();
		method = "POST";
		omitXmlDeclaration = false;
		jsonToXml = true;
		jsonToXmlTypeHints = true;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method.toUpperCase();
	}

	public Map<String, String> getData() {
		return data;
	}

	public String getResultInsertPoint() {
		return resultInsertPoint;
	}

	public void setResultInsertPoint(String resultInsertPoint) {
		this.resultInsertPoint = resultInsertPoint;
	}

	public String getPreTransform() {
		return preTransform;
	}

	public void setPreTransform(String preTransform) {
		this.preTransform = preTransform;
	}

	public String getPostTransform() {
		return postTransform;
	}

	public void setPostTransform(String postTransform) {
		this.postTransform = postTransform;
	}

	public String getMessageOnHttpError() {
		return messageOnHttpError;
	}

	public void setMessageOnHttpError(String errorMessage) {
		this.messageOnHttpError = errorMessage;
	}

	public boolean isOmitXmlDeclaration() {
		return omitXmlDeclaration;
	}

	public void setOmitXmlDeclaration(boolean omitXmlDeclaration) {
		this.omitXmlDeclaration = omitXmlDeclaration;
	}

	public boolean isRawXmlRequest() {
		return rawXmlRequest;
	}

	public void setRawXmlRequest(boolean rawXmlRequest) {
		this.rawXmlRequest = rawXmlRequest;
	}

	public boolean isJsonToXml() {
		return jsonToXml;
	}

	public void setJsonToXml(boolean jsonToXml) {
		this.jsonToXml = jsonToXml;
	}

	public boolean isJsonToXmlTypeHints() {
		return jsonToXmlTypeHints;
	}

	public void setJsonToXmlTypeHints(boolean jsonToXmlTypeHints) {
		this.jsonToXmlTypeHints = jsonToXmlTypeHints;
	}

	public String getJsonToXmlRootName() {
		return jsonToXmlRootName;
	}

	public void setJsonToXmlRootName(String jsonToXmlRootName) {
		this.jsonToXmlRootName = jsonToXmlRootName;
	}

}
