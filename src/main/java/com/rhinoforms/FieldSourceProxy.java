package com.rhinoforms;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import javax.servlet.http.HttpServletResponse;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rhinoforms.util.StreamUtils;

public class FieldSourceProxy {

	private String url;
	private String proxyPath;
	
	private static StreamUtils streamUtils = new StreamUtils();

	final Logger logger = LoggerFactory.getLogger(FieldSourceProxy.class);

	public FieldSourceProxy(String proxyPath, String url) {
		this.proxyPath = proxyPath;
		this.url = url;
	}

	public String getUrl() {
		return url;
	}

	public String getProxyPath() {
		return proxyPath;
	}

	public void makeRequest(String value, HttpServletResponse response) throws FieldSourceProxyException {
		String thisUrl = url.replace("{value}", value);
		logger.debug("Proxying url {}", thisUrl);
		
		DefaultHttpClientConnection conn = null;
		try {
			URI targetURI = new URI(thisUrl);
			
			HttpHost targetHost = new HttpHost(targetURI.getHost(), targetURI.getPort());
	
			String path = thisUrl;
			if (path.contains("//")) {
				path = path.substring(path.indexOf("//") + 2);
			}
			if (path.contains("/")) {
				path = path.replace(path.split("/")[0], "");
			}
			
			BasicHttpRequest httpget = new BasicHttpRequest("GET", path);
			BasicHttpParams params = new BasicHttpParams();
			httpget.setParams(params);
	
			HttpRequestExecutor httpexecutor = new HttpRequestExecutor();
			BasicHttpProcessor httpproc = new BasicHttpProcessor();
			// Required protocol interceptors
			httpproc.addInterceptor(new RequestContent());
			httpproc.addInterceptor(new RequestTargetHost());
			// Recommended protocol interceptors
			httpproc.addInterceptor(new RequestConnControl());
			httpproc.addInterceptor(new RequestUserAgent());
			httpproc.addInterceptor(new RequestExpectContinue());
	
			HttpContext context = new BasicHttpContext();
	
			conn = new DefaultHttpClientConnection();
			Socket socket = new Socket(targetHost.getHostName(), targetHost.getPort() > 0 ? targetHost.getPort() : 80);
			conn.bind(socket, params);
	
			context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
			context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, targetHost);
	
			httpexecutor.preProcess(httpget, httpproc, context);
			HttpResponse proxyResponse = httpexecutor.execute(httpget, conn, context);
			httpexecutor.postProcess(proxyResponse, httpproc, context);
	
			HttpEntity entity = proxyResponse.getEntity();
			if (entity != null) {
				
				long contentLength = entity.getContentLength();
				if (contentLength > 0 && contentLength <= Integer.MAX_VALUE) {
					response.setContentLength((int) contentLength);
				}
				
				Header contentType = entity.getContentType();
				if (contentType != null) {
					response.setHeader(contentType.getName(), contentType.getValue());
				}
				
				Header contentEncoding = entity.getContentEncoding();
				if (contentEncoding != null) {
					response.setHeader(contentEncoding.getName(), contentEncoding.getValue());
				}
				
				InputStream inputStream = entity.getContent();
				try {
					streamUtils.copyInputStreamToOutputStream(inputStream, response.getOutputStream());
				} catch (IOException ex) {
					conn.shutdown();
				} finally {
					inputStream.close();
				}
			}
		} catch (IllegalArgumentException e) {
			logger.error("Proxy URL problem '{}'", thisUrl, e);
			throw new FieldSourceProxyException();
		} catch (URISyntaxException e) {
			logger.error("Proxy URL problem '{}'", thisUrl, e);
			throw new FieldSourceProxyException();
		} catch (UnknownHostException e) {
			logger.error("Proxy URL problem '{}'", thisUrl, e);
			throw new FieldSourceProxyException();
		} catch (IOException e) {
			logger.error("Proxy URL problem '{}'", thisUrl, e);
			throw new FieldSourceProxyException();
		} catch (HttpException e) {
			logger.error("Proxy URL problem '{}'", thisUrl, e);
			throw new FieldSourceProxyException();
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (IOException e) {
					logger.error("Failed to close connection to proxy url.", e);
				}
			}
		}
	}

	public static void main(String[] args) throws FieldSourceProxyException {
		String url = "http://www.bigggg.com/resource-manager/REST/files/itb-resources-test2/lists/_itbLists-occupationSheet.xml?transformer=xslt&xslt=/itb-resources-test2/lists/occupationLookupTransformJSONmin.xsl&contentType=application/json&occupation={value}";
		new FieldSourceProxy("123", url).makeRequest("Bri", null);
	}

}
