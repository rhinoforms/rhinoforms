package com.rhinoforms.flow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.Map;

import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathExpressionException;

import net.sf.json.JSON;
import net.sf.json.JSONSerializer;
import net.sf.json.xml.XMLSerializer;

import org.apache.commons.io.IOUtils;
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
	private ResourceLoader resourceLoader;
	private TransformHelper transformHelper;
	private ValueInjector valueInjector;
	private StreamUtils streamUtils;
	private static final String UTF8 = "UTF-8";
	private static final String DATA_DOCUMENT_VALUE_KEY = "[dataDocument]";
	private static final Logger LOGGER = LoggerFactory.getLogger(RemoteSubmissionHelper.class);
	private static final String HTML_TEMPLATE_PREFIX = "htmlTemplate:";

	public RemoteSubmissionHelper(ResourceLoader resourceLoader, ValueInjector valueInjector, TransformHelper transformHelper) {
		this.resourceLoader = resourceLoader;
		this.valueInjector = valueInjector;
		this.transformHelper = transformHelper;
		connectionFactory = new ConnectionFactoryImpl();
		documentHelper = new DocumentHelper();
		streamUtils = new StreamUtils();
	}

	public void handleSubmission(Submission submission, Map<String, String> xsltParameters, FormFlow formFlow)
			throws RemoteSubmissionHelperException, FlowExceptionXPath {
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
				message = "transforming Data Document using preTransform for submission.";
				StringWriter transformResultWriter = new StringWriter();
				transformHelper.handleTransform(preTransform, submission.isOmitXmlDeclaration(), xsltParameters, dataDocument, new StreamResult(transformResultWriter));
				dataDocumentString = transformResultWriter.toString();
				LOGGER.debug("preTransform result: {}", dataDocumentString);
			} else {
				LOGGER.debug("No transform provided");
				message = "transforming Data Document into a String for submission.";
				StringWriter stringWriter = new StringWriter();
				documentHelper.documentToWriter(dataDocument, stringWriter, submission.isOmitXmlDeclaration());
				dataDocumentString = stringWriter.toString();
			}
		} catch (TransformerException e) {
			throw new RemoteSubmissionHelperException("Error while " + message, e);
		} catch (IOException e) {
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
							} else if (dataValue.startsWith(HTML_TEMPLATE_PREFIX)) {
								String templatePath = dataValue.substring(HTML_TEMPLATE_PREFIX.length());
								templatePath = formFlow.resolveResourcePathIfRelative(templatePath);
								InputStream templateStream = resourceLoader.getFormResourceAsStream(templatePath);
								ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
								valueInjector.processHtmlTemplate(templateStream, dataDocument, "*", formFlow.getProperties(), outputStream);
								dataValue = outputStream.toString();
							} else if (dataValue.contains("{{")) {
								StringBuilder stringBuilder = new StringBuilder(dataValue);
								valueInjector.replaceCurlyBrackets(formFlow.getProperties(), stringBuilder, formFlow.getDataDocument());
								dataValue = stringBuilder.toString();
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
			throw new RemoteSubmissionHelperException("Failed to encode values for submission request.", e);
		} catch (XPathExpressionException e) {
			throw new RemoteSubmissionHelperException("Failed to build values for submission request. XPathExpressionException.", e);
		} catch (IOException e) {
			throw new RemoteSubmissionHelperException("Failed to build values for submission request. IOException.", e);
		} catch (ValueInjectorException e) {
			throw new RemoteSubmissionHelperException("Failed to process HTML Template for submission request.", e);
		}
		

		LOGGER.debug("Submission data: {}", requestDataString);
		
		StringBuilder urlBuilder = new StringBuilder(url);
		if (urlBuilder.indexOf("{{") != -1) {
			try {
				valueInjector.replaceCurlyBrackets(formFlow.getProperties(), urlBuilder, dataDocument);
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
			resolvedUrl = resolvedUrl.replaceAll(" ", "+");
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
						
						Node insertPointNode = documentHelper.lookupOrCreateNode(dataDocument, resultInsertPoint);
						
						if (contentType != null && contentType.startsWith("text/plain")) {
							byte[] streamData = streamUtils.readStream(inputStream);
							insertPointNode.setTextContent(new String(streamData));
						} else {
							Document resultDocument;
							if (contentType != null && contentType.startsWith("application/json")) {
								String jsonData = IOUtils.toString(inputStream);
								JSON json = JSONSerializer.toJSON(jsonData);
								
								XMLSerializer xmlSerializer = new XMLSerializer();
								xmlSerializer.setTypeHintsEnabled(submission.isJsonToXmlTypeHints());
								String rootName = submission.getJsonToXmlRootName();
								if (rootName != null) {
									xmlSerializer.setRootName(rootName);
								}
								
								String xml = xmlSerializer.write(json);
								resultDocument = documentHelper.streamToDocument(new ByteArrayInputStream(xml.getBytes()));
							} else {
								resultDocument = documentHelper.streamToDocument(inputStream);
							}
	
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

							NodeList childNodes = insertPointNode.getChildNodes();
							for (int i = 0; i < childNodes.getLength(); i++) {
								insertPointNode.removeChild(childNodes.item(i));
							}
							insertPointNode.appendChild(importedNode);
						}
					} else {
						LOGGER.info("Response body: {}", new String(new StreamUtils().readStream(connection.getInputStream())));
					}
				} finally {
					inputStream.close();
				}
			} else {
				throw new RemoteSubmissionHelperException("Bad response from target service. Status:" + responseCode + ", message:"
						+ connection.getResponseMessage(), submission.getMessageOnHttpError());
			}
		} catch (ConnectException e) {
			throw new RemoteSubmissionHelperException("Failed to connect to a service that this form uses.", submission.getMessageOnHttpError(), e);
		} catch (IOException e) {
			throw new RemoteSubmissionHelperException("IOException while handling submission.", e);
		} catch (TransformerException e) {
			throw new RemoteSubmissionHelperException("Failed to transform the submission response document.", e);
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
