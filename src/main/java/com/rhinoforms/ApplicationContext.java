package com.rhinoforms;

import java.io.IOException;

import javax.servlet.ServletContext;

import org.mozilla.javascript.Context;

import com.rhinoforms.flow.FormFlowFactory;
import com.rhinoforms.flow.FormSubmissionHelper;
import com.rhinoforms.flow.RemoteSubmissionHelper;
import com.rhinoforms.flow.SubmissionTimeKeeper;
import com.rhinoforms.formparser.FormParser;
import com.rhinoforms.formparser.ValueInjector;
import com.rhinoforms.js.JSMasterScope;
import com.rhinoforms.js.RhinoFormsMasterScopeFactory;
import com.rhinoforms.resourceloader.ClasspathResourceLoader;
import com.rhinoforms.resourceloader.ResourceLoader;
import com.rhinoforms.resourceloader.ResourceLoaderException;
import com.rhinoforms.resourceloader.ResourceLoaderFactory;
import com.rhinoforms.resourceloader.ResourceLoaderImpl;
import com.rhinoforms.resourceloader.ServletContextResourceLoader;
import com.rhinoforms.resourceloader.SingleSourceResourceLoader;
import com.rhinoforms.util.ServletHelper;
import com.rhinoforms.xml.DocumentHelper;

public class ApplicationContext {

	private RhinoformsProperties rhinoformsProperties;
	private ResourceLoader resourceLoader;
	private ServletContext servletContext;
	private DocumentHelper documentHelper;
	private FormParser formParser;
	private SubmissionTimeKeeper submissionTimeKeeper;
	private JSMasterScope masterScope;
	private FormSubmissionHelper formSubmissionHelper;
	private FormFlowFactory formFlowFactory;
	private RemoteSubmissionHelper remoteSubmissionHelper;
	private FormProducer formProducer;
	private FlowRequestFactory flowRequestFactory;
	private FormActionRequestFactory formActionRequestFactory;
	private ServletHelper servletHelper;

	public ApplicationContext(ServletContext servletContext) throws ResourceLoaderException, IOException {
		Context jsContext = Context.enter();
		try {
			this.servletContext = servletContext;
			this.rhinoformsProperties = RhinoformsProperties.getInstance();
			this.resourceLoader = createResourceLoader();
			this.documentHelper = new DocumentHelper();
			this.submissionTimeKeeper = new SubmissionTimeKeeper();
			this.formParser = new FormParser(resourceLoader, submissionTimeKeeper);
			this.masterScope = new RhinoFormsMasterScopeFactory().createMasterScope(jsContext, resourceLoader);
			this.formSubmissionHelper = new FormSubmissionHelper(masterScope);
			this.formFlowFactory = new FormFlowFactory(resourceLoader, masterScope, servletContext.getContextPath(), submissionTimeKeeper);
			this.remoteSubmissionHelper = new RemoteSubmissionHelper(resourceLoader, new ValueInjector());
			this.flowRequestFactory = new FlowRequestFactory();
			this.servletHelper = new ServletHelper();
			this.formActionRequestFactory = new FormActionRequestFactory(servletHelper);
			this.formProducer = new FormProducer(this);
		} finally {
			Context.exit();
		}
	}

	private ResourceLoader createResourceLoader() throws ResourceLoaderException {
		String formResourcesSource = rhinoformsProperties.getFormResourceLoader();
		
		SingleSourceResourceLoader webappResourceLoader = new ServletContextResourceLoader(servletContext);
		SingleSourceResourceLoader formResourceLoader;
		
		if (formResourcesSource != null && !formResourcesSource.isEmpty()) {
			formResourceLoader = new ResourceLoaderFactory(servletContext).createResourceLoader(formResourcesSource);
		} else {
			formResourceLoader = new ClasspathResourceLoader();
		}
		return new ResourceLoaderImpl(webappResourceLoader, formResourceLoader);
	}
	
	public ResourceLoader getResourceLoader() {
		return resourceLoader;
	}
	
	public DocumentHelper getDocumentHelper() {
		return documentHelper;
	}

	public RhinoformsProperties getRhinoformsProperties() {
		return rhinoformsProperties;
	}

	public FormParser getFormParser() {
		return formParser;
		
	}

	public SubmissionTimeKeeper getSubmissionTimeKeeper() {
		return submissionTimeKeeper;
	}

	public JSMasterScope getMasterScope() {
		return masterScope;
	}
	
	public FormSubmissionHelper getFormSubmissionHelper() {
		return formSubmissionHelper;
	}
	
	public FormFlowFactory getFormFlowFactory() {
		return formFlowFactory;
	}
	
	public RemoteSubmissionHelper getRemoteSubmissionHelper() {
		return remoteSubmissionHelper;
	}
	
	public FormProducer getFormProducer() {
		return formProducer;
	}
	
	public FlowRequestFactory getFlowRequestFactory() {
		return flowRequestFactory;
	}
	
	public FormActionRequestFactory getFormActionRequestFactory() {
		return formActionRequestFactory;
	}
	
	public ServletContext getServletContext() {
		return servletContext;
	}
	
	public ServletHelper getServletHelper() {
		return servletHelper;
	}
	
}