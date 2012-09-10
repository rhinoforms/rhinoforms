package com.rhinoforms;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

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

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.rhinoforms.net.ConnectionFactory;
import com.rhinoforms.resourceloader.ResourceLoader;

public class RemoteSubmissionHelper {

	private ConnectionFactory connectionFactory;
	private DocumentHelper documentHelper;
	private ResourceLoader resourceLoader;
	private TransformerFactory transformerFactory;
	
	public RemoteSubmissionHelper(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
		connectionFactory = new ConnectionFactoryImpl();
		documentHelper = new DocumentHelper();
		transformerFactory = TransformerFactory.newInstance();
	}
	
	public void handleSubmission(Submission submission, Document dataDocument) throws RemoteSubmissionHelperException {
		
		String dataDocumentPreTransformed = null;
		String preTransform = submission.getPreTransform();
		String postTransform = submission.getPostTransform();
		
		if (preTransform != null) {
			String errorMessage = "Error while transforming Data Document before submission.";
			try {
				Transformer transformer = getTransformer(preTransform);
				StringWriter requestDataAfterTransformWriter = new StringWriter();
				transformer.transform(new DOMSource(dataDocument), new StreamResult(requestDataAfterTransformWriter));
				dataDocumentPreTransformed = requestDataAfterTransformWriter.toString();
			} catch (TransformerException e) {
				throw new RemoteSubmissionHelperException(errorMessage, e);
			} catch (IOException e) {
				throw new RemoteSubmissionHelperException(errorMessage, e);
			}
		}
		
		String url = submission.getUrl();
		try {
			HttpURLConnection connection = connectionFactory.openConnection(url);
			
			OutputStream outputStream = connection.getOutputStream();
			OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
			if (dataDocumentPreTransformed != null) {
				outputStreamWriter.write(dataDocumentPreTransformed);
			} else {
				documentHelper.documentToWriter(dataDocument, outputStreamWriter);
			}
			outputStreamWriter.close();
			
			int responseCode = connection.getResponseCode();
			if (responseCode == 200) {
				String resultInsertPoint = submission.getResultInsertPoint();
				if (resultInsertPoint != null) {
					InputStream inputStream = connection.getInputStream();
					
					Node nodeToImport = null;
					if (postTransform != null) {
						Transformer transformer = getTransformer(postTransform);
						DOMResult domResult = new DOMResult();
						transformer.transform(new StreamSource(inputStream), domResult);
						nodeToImport = domResult.getNode().getChildNodes().item(0);
					} else {
						Document resultDocument = documentHelper.streamToDocument(inputStream);
						nodeToImport = resultDocument.getChildNodes().item(0);
					}
					
					Node importedNode = dataDocument.importNode(nodeToImport, true);
					Node insertPointNode = documentHelper.lookupOrCreateNode(dataDocument, resultInsertPoint);
					insertPointNode.appendChild(importedNode);
				}
			} else {
				throw new RemoteSubmissionHelperException("Bad response from target service. Status:" + responseCode + ", message:" + connection.getResponseMessage());
			}
		} catch (IOException e) {
			throw new RemoteSubmissionHelperException("IOException while handling submission.", e);
		} catch (TransformerException e) {
			throw new RemoteSubmissionHelperException("Failed to serialise the DataDocument for submission.", e);
		} catch (DocumentHelperException e) {
			throw new RemoteSubmissionHelperException("Failed to insert submission result into the DataDocument.", e);
		}
	}

	private Transformer getTransformer(String preTransform) throws FileNotFoundException, TransformerFactoryConfigurationError,
			TransformerConfigurationException {
		InputStream preTransformStream = resourceLoader.getResourceAsStream(preTransform);
		Transformer transformer = transformerFactory.newTransformer(new StreamSource(preTransformStream));
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		return transformer;
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
