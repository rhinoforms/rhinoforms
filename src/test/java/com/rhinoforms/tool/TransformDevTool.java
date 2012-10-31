package com.rhinoforms.tool;

import java.io.File;
import java.io.OutputStreamWriter;

import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.TransformerFactoryImpl;

import com.rhinoforms.DocumentHelper;

public class TransformDevTool {

	private TransformerFactoryImpl transformerFactory;
	private DocumentHelper documentHelper;

	public static void main(String[] args) throws Exception {
		new TransformDevTool().run();
	}
	
	public TransformDevTool() {
		transformerFactory = new TransformerFactoryImpl();
		documentHelper = new DocumentHelper();
	}
	
	private void run() throws Exception {
		System.out.println("Start");
		
		Transformer transformer = transformerFactory.newTransformer(new StreamSource(new File("/home/kai/svn_source/rhinoforms-forms/rias/apc-stage1/xslt/toNapier.xsl")));
		transformer.setParameter("rf.flowId", "123");
		transformer.setParameter("rf.formId", "home");
		transformer.setParameter("rf.actionName", "next");
		DOMResult domResult = new DOMResult();
		transformer.transform(new StreamSource(new File("/home/kai/svn_source/rhinoforms-forms/rias/apc-stage1/test-data/toNapierInput.xml")), domResult);
		documentHelper.documentToWriterPretty(domResult.getNode(), new OutputStreamWriter(System.out));
		
		System.out.println("End");
	}
	
}
