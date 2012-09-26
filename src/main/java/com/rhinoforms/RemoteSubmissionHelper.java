package com.rhinoforms;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.rhinoforms.net.ConnectionFactory;
import com.rhinoforms.resourceloader.ResourceLoader;

public class RemoteSubmissionHelper {

	private ConnectionFactory connectionFactory;
	private DocumentHelper documentHelper;
	private ResourceLoader resourceLoader;
	private TransformerFactory transformerFactory;
	private static final String UTF8 = "UTF-8";
	private static final String DATA_DOCUMENT_VALUE_KEY = "[dataDocument]";
	private static final Logger LOGGER = LoggerFactory.getLogger(RemoteSubmissionHelper.class);

	public RemoteSubmissionHelper(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
		connectionFactory = new ConnectionFactoryImpl();
		documentHelper = new DocumentHelper();
		transformerFactory = TransformerFactory.newInstance();
	}

	public void handleSubmission(Submission submission, Document dataDocument) throws RemoteSubmissionHelperException {
		String url = submission.getUrl();
		String method = submission.getMethod();
		Map<String, String> data = submission.getData();
		String preTransform = submission.getPreTransform();
		String postTransform = submission.getPostTransform();

		LOGGER.debug("Handling '{}' submission to '{}'", method, url);

		String dataDocumentString;
		String errorMessage = null;
		try {
			if (preTransform != null) {
				errorMessage = "Error while transforming Data Document using preTransform for submission.";
				Transformer transformer = getTransformer(preTransform);
				StringWriter requestDataAfterTransformWriter = new StringWriter();
				transformer.transform(new DOMSource(dataDocument), new StreamResult(requestDataAfterTransformWriter));
				dataDocumentString = requestDataAfterTransformWriter.toString();
			} else {
				errorMessage = "Error while transforming Data Document into a String for submission.";
				dataDocumentString = documentHelper.documentToString(dataDocument);
			}
		} catch (TransformerException e) {
			throw new RemoteSubmissionHelperException(errorMessage, e);
		} catch (IOException e) {
			throw new RemoteSubmissionHelperException(errorMessage, e);
		}

		StringBuilder requestDataBuilder = new StringBuilder();
		if (!data.isEmpty()) {
			try {
				boolean first = true;
				for (String key : data.keySet()) {
					if (first) {
						first = false;
					} else {
						requestDataBuilder.append("&");
					}
					requestDataBuilder.append(URLEncoder.encode(key, UTF8));
					requestDataBuilder.append("=");
					String dataValue = data.get(key);
					if (DATA_DOCUMENT_VALUE_KEY.equals(dataValue)) {
						dataValue = dataDocumentString;
					}
					requestDataBuilder.append(URLEncoder.encode(dataValue, UTF8));
				}
			} catch (UnsupportedEncodingException e) {
				throw new RemoteSubmissionHelperException("Failed to encode values for submission", e);
			}
		}
		String requestDataString = requestDataBuilder.toString();

		// If data not being sent in body, add as URL parameters
		if (!method.equals("POST")) {
			if (!url.contains("?")) {
				url += "?";
			} else {
				url += "&";
			}
			url += requestDataString;
		}

		LOGGER.debug("Submission data: {}", requestDataString);
		
		try {
			HttpURLConnection connection = connectionFactory.openConnection(url);

			connection.setRequestMethod(method.toUpperCase());

			if (method.equals("POST") && !requestDataString.isEmpty()) {
				connection.setDoOutput(true);
				OutputStream outputStream = connection.getOutputStream();
				OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
				outputStreamWriter.write(requestDataString);
				outputStreamWriter.close();
			}

			int responseCode = connection.getResponseCode();
			if (responseCode == 200) {
				String resultInsertPoint = submission.getResultInsertPoint();
				if (resultInsertPoint != null) {
					InputStream inputStream = connection.getInputStream();
					
					Document resultDocument = documentHelper.streamToDocument(inputStream);

					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("Result document: {}", documentHelper.documentToString(resultDocument));
					}
					
					Node nodeToImport = null;
					if (postTransform != null) {
						Transformer transformer = getTransformer(postTransform);
						DOMResult domResult = new DOMResult();
						transformer.transform(new DOMSource(resultDocument), domResult);
						nodeToImport = domResult.getNode().getChildNodes().item(0);
						if (LOGGER.isDebugEnabled()) {
							LOGGER.debug("Transformed result: {}", documentHelper.documentToString(nodeToImport));
						}
					} else {
						nodeToImport = resultDocument.getChildNodes().item(0);
					}

					Node importedNode = dataDocument.importNode(nodeToImport, true);
					Node insertPointNode = documentHelper.lookupOrCreateNode(dataDocument, resultInsertPoint);
					insertPointNode.appendChild(importedNode);
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

	private Transformer getTransformer(String preTransform) throws FileNotFoundException, TransformerFactoryConfigurationError,
			TransformerConfigurationException {
		InputStream preTransformStream = resourceLoader.getResourceAsStream(preTransform);
		if (preTransformStream != null) {
			Transformer transformer = transformerFactory.newTransformer(new StreamSource(preTransformStream));
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			return transformer;
		} else {
			throw new FileNotFoundException(preTransform);
		}
	}

	public void setConnectionFactory(ConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
	}

	private static class ConnectionFactoryImpl implements ConnectionFactory {

		@Override
		public HttpURLConnection openConnection(String url) throws MalformedURLException, IOException {
			return (HttpURLConnection) new URL(url).openConnection();
		}

	}

}
