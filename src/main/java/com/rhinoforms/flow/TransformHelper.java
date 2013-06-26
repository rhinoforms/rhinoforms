package com.rhinoforms.flow;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.TransformerFactoryImpl;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.rhinoforms.resourceloader.ResourceLoader;

public class TransformHelper {
	
	private ResourceLoader resourceLoader;
	private TransformerFactory transformerFactory;

	public TransformHelper(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
		transformerFactory = new TransformerFactoryImpl(); // Saxon Impl
	}

	public void handleTransform(String transformXsl, boolean omitXmlDeclaration, Map<String, String> xsltParameters, Document dataDocument, Result transformResult) throws TransformerException, IOException {
		Transformer transformer = getTransformer(transformXsl, omitXmlDeclaration);
		if (xsltParameters != null) {
			for (String paramKey : xsltParameters.keySet()) {
				transformer.setParameter(paramKey, xsltParameters.get(paramKey));
			}
		}
		transformer.transform(new DOMSource(dataDocument), transformResult);
	}
	
	public Node handleTransform(String transformXsl, boolean omitXmlDeclaration, Document dataDocument) throws TransformerException, IOException {
		Node node = null;
		
		Transformer transformer = getTransformer(transformXsl, true);
		DOMResult domResult = new DOMResult();
		transformer.transform(new DOMSource(dataDocument), domResult);
		node = domResult.getNode().getChildNodes().item(0);
		
		return node;
	}

	private Transformer getTransformer(String transformXsl, boolean omitXmlDeclaration) throws IOException,
			TransformerConfigurationException {
		InputStream preTransformStream = resourceLoader.getFormResourceAsStream(transformXsl);
		if (preTransformStream != null) {
			Transformer transformer = transformerFactory.newTransformer(new StreamSource(preTransformStream));
			if (omitXmlDeclaration) {
				transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			}
			return transformer;
		} else {
			throw new FileNotFoundException(transformXsl);
		}
	}

}
