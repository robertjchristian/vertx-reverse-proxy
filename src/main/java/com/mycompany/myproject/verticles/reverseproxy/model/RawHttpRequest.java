package com.mycompany.myproject.verticles.reverseproxy.model;

import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;

public class RawHttpRequest {

	private String method;
	private String uri;
	private MultiMap headers;
	private Buffer body;

	public RawHttpRequest(final HttpServerRequest req) {
		this.method = req.method();
		this.uri = req.absoluteURI().toString();
		this.headers = req.headers();
		System.out.println(req.headers().getClass().getName());
		req.dataHandler(new Handler<Buffer>() {

			@Override
			public void handle(Buffer buffer) {
				body = buffer;
			}
		});
		req.endHandler(new VoidHandler() {
			@Override
			protected void handle() {

			}
		});
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public MultiMap getHeaders() {
		return headers;
	}

	public void setHeaders(MultiMap headers) {
		this.headers = headers;
	}

	public Buffer getBody() {
		return body;
	}

	public void setBody(Buffer body) {
		this.body = body;
	}
}
