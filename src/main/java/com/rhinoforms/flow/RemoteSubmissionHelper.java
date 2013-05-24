package com.rhinoforms.flow;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.Map;

import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import com.rhinoforms.formparser.ValueInjector;
import com.rhinoforms.formparser.ValueInjectorException;
import com.rhinoforms.net.ConnectionFactory;
import com.rhinoforms.net.ConnectionFactoryImpl;
import com.rhinoforms.resourceloader.ResourceLoader;
import com.rhinoforms.util.StreamUtils;
import com.rhinoforms.xml.DocumentHelper;
import com.rhinoforms.xml.DocumentHelperException;
import com.rhinoforms.xml.FlowExceptionXPath;

public class RemoteSubmissionHelper {

	private ConnectionFactory connectionFactory;
	private DocumentHelper documentHelper;
	private TransformHelper transformHelper;
	private ValueInjector valueInjector;
	private static final String UTF8 = "UTF-8";
	private static final String DATA_DOCUMENT_VALUE_KEY = "[dataDocument]";
	private static final Logger LOGGER = LoggerFactory.getLogger(RemoteSubmissionHelper.class);

	public RemoteSubmissionHelper(ResourceLoader resourceLoader, ValueInjector valueInjector) {
		this.valueInjector = valueInjector;
		connectionFactory = new ConnectionFactoryImpl();
		documentHelper = new DocumentHelper();
	}

	public void handleSubmission(Submission submission, Map<String, String> xsltParameters, FormFlow formFlow)
			throws RemoteSubmissionHelperException, FlowExceptionXPath, TransformerException {
		String url = submission.getUrl();
		String method = submission.getMethod();
		Map<String, String> data = submission.getData();
		String preTransform = submission.getPreTransform();
		String postTransform = submission.getPostTransform();
		boolean rawXmlRequest = submission.isRawXmlRequest();
		Document dataDocument = formFlow.getDataDocument();

		LOGGER.debug("Handling '{}' submission to '{}'", method, url);

		String dataDocumentString;
		String message = null;
		try {
			if (preTransform != null) {
				dataDocumentString = transformHelper.handleTransform(preTransform, submission.isOmitXmlDeclaration(), xsltParameters, dataDocument);
			} else {
				LOGGER.debug("No transform provided");
				message = "transforming Data Document into a String for submission.";
				StringWriter stringWriter = new StringWriter();
				documentHelper.documentToWriter(dataDocument, stringWriter, submission.isOmitXmlDeclaration());
				dataDocumentString = stringWriter.toString();
			}
		} catch (TransformHelperException e) {
			throw new RemoteSubmissionHelperException("Error while " + message, e);
		} 

		String requestDataString;
		try {
			if (!rawXmlRequest) {
				StringBuilder requestDataBuilder = new StringBuilder();
				if (!data.isEmpty()) {
					boolean first = true;
					for (String key : data.keySet()) {
						String dataValue = data.get(key);
						if (!first) {
							requestDataBuilder.append("&");
						}
						first = false;
						
						if (dataValue.startsWith("xpath:")) {
							NodeList nodeList = documentHelper.lookup(dataDocument, dataValue.substring(6));
							boolean firstNode = true;
							if (nodeList.getLength() > 0) {
								for (int i = 0; i < nodeList.getLength(); i++) {
									
									// We want the first match to always append the parameter name even if no value.
									if (firstNode) {
										requestDataBuilder.append(URLEncoder.encode(key, UTF8));
										requestDataBuilder.append("=");
									}
									
									Node node = nodeList.item(i);
									Node firstChild = node.getFirstChild();
									if (firstChild instanceof Text) {
										String textContent = firstChild.getTextContent();
										if (!firstNode) {
											requestDataBuilder.append("&");
											requestDataBuilder.append(URLEncoder.encode(key, UTF8));
											requestDataBuilder.append("=");
										}
										requestDataBuilder.append(URLEncoder.encode(textContent, UTF8));
										firstNode = false;
									}
								}
							} else {
								first = true;
							}
						} else {
							requestDataBuilder.append(URLEncoder.encode(key, UTF8));
							requestDataBuilder.append("=");
							if (DATA_DOCUMENT_VALUE_KEY.equals(dataValue)) {
								dataValue = dataDocumentString;
							}
							requestDataBuilder.append(URLEncoder.encode(dataValue, UTF8));
						}
					}
				}
				requestDataString = requestDataBuilder.toString();
			} else {
				requestDataString = dataDocumentString;
			}
			
		} catch (UnsupportedEncodingException e) {
			throw new RemoteSubmissionHelperException("Failed to encode values for submission", e);
		} catch (XPathExpressionException e) {
			throw new RemoteSubmissionHelperException("Failed to build values for submission. XPathExpressionException.", e);
		}
		

		LOGGER.debug("Submission data: {}", requestDataString);
		
		StringBuilder urlBuilder = new StringBuilder(url);
		if (urlBuilder.indexOf("{{") != -1) {
			try {
				valueInjector.replaceCurlyBrackets(formFlow, urlBuilder, dataDocument);
			} catch (ValueInjectorException e) {
				throw new RemoteSubmissionHelperException("Failed to build submission URL.", e);
			}
		}

		try {
			if (!requestDataString.isEmpty() && !method.equals("POST")) {
				if (urlBuilder.indexOf("?") == -1) {
					urlBuilder.append("?");
				} else {
					urlBuilder.append("&");
				}
				urlBuilder.append(requestDataString);
			}
			
			String resolvedUrl = urlBuilder.toString();
			if (!url.equals(resolvedUrl)) {
				LOGGER.debug("Resolved url: '{}'", resolvedUrl);
			}
			
			HttpURLConnection connection = connectionFactory.openConnection(resolvedUrl);

			connection.setRequestMethod(method.toUpperCase());

			if (method.equals("POST")) {
				connection.setRequestProperty("Content-Type", rawXmlRequest ? "application/xml" : "application/x-www-form-urlencoded");
				connection.setDoOutput(true);
				OutputStream outputStream = connection.getOutputStream();
				OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
				outputStreamWriter.write(requestDataString);
				outputStreamWriter.close();
			}

			int responseCode = connection.getResponseCode();
			if (responseCode == 200) {
				String resultInsertPoint = submission.getResultInsertPoint();
				String contentType = connection.getContentType();
				LOGGER.info("Response content type: {}", contentType);
				InputStream inputStream = connection.getInputStream();
				try {
					if (resultInsertPoint != null) {
						Document resultDocument = documentHelper.streamToDocument(inputStream);

						if (LOGGER.isDebugEnabled()) {
							LOGGER.debug("Result document: {}", documentHelper.documentToString(resultDocument));
						}

						Node nodeToImport = null;
						if (postTransform != null) {
							nodeToImport = transformHelper.handleTransform(postTransform, true, resultDocument);
							if (LOGGER.isDebugEnabled()) {
								LOGGER.debug("Transformed result: {}", documentHelper.documentToString(nodeToImport));
							}
						} else {
							nodeToImport = resultDocument.getChildNodes().item(0);
						}

						Node importedNode = dataDocument.importNode(nodeToImport, true);
						Node insertPointNode = documentHelper.lookupOrCreateNode(dataDocument, resultInsertPoint);

						NodeList childNodes = insertPointNode.getChildNodes();
						for (int i = 0; i < childNodes.getLength(); i++) {
							insertPointNode.removeChild(childNodes.item(i));
						}
						insertPointNode.appendChild(importedNode);
					} else {
						LOGGER.info("Response body: {}", new String(new StreamUtils().readStream(connection.getInputStream())));
					}
				} finally {
					inputStream.close();
				}
			} else {
				throw new RemoteSubmissionHelperException("Bad response from target service. Status:" + responseCode + ", message:"
						+ connection.getResponseMessage());
			}
		} catch (IOException e) {
			throw new RemoteSubmissionHelperException("IOException while handling submission.", e);
		} catch (TransformerException e) {
			throw new RemoteSubmissionHelperException(
					"Failed to transform the submission response document serialise the DataDocument for submission.", e);
		} catch (DocumentHelperException e) {
			throw new RemoteSubmissionHelperException("Failed to insert submission result into the DataDocument.", e);
		}
	}

	public void setConnectionFactory(ConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
	}

	public void setTransformHelper(TransformHelper transformHelper) {
		this.transformHelper = transformHelper;
	}

}
