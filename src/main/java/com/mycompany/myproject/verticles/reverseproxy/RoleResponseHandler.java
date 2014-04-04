package com.mycompany.myproject.verticles.reverseproxy;

import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.serviceDependencyConfig;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import com.mycompany.myproject.verticles.reverseproxy.model.SessionToken;

public class RoleResponseHandler implements Handler<HttpClientResponse> {

	private static final Logger log = LoggerFactory.getLogger(RoleResponseHandler.class);

	private final HttpServerRequest req;

	private final Vertx vertx;

	private final String payload;

	private final SessionToken sessionToken;

	private final boolean authPosted;

	public RoleResponseHandler(Vertx vertx, HttpServerRequest req, String payload, SessionToken sessionToken, boolean authPosted) {
		this.req = req;
		this.vertx = vertx;
		this.payload = payload;
		this.sessionToken = sessionToken;
		this.authPosted = authPosted;
	}

	@Override
	public void handle(final HttpClientResponse res) {

		res.dataHandler(new Handler<Buffer>() {
			@Override
			public void handle(Buffer data) {

				// role fetch successful
				if (res.statusCode() >= 200 && res.statusCode() < 300) {
					log.debug("Successfully fetched role. Getting manifest from ACL");
					HttpClient signClient = vertx.createHttpClient()
							.setHost(serviceDependencyConfig.getHost("acl"))
							.setPort(serviceDependencyConfig.getPort("acl"));
					final HttpClientRequest roleRequest = signClient.request("POST",
							serviceDependencyConfig.getRequestPath("acl", "manifest"),
							new ManifestResponseHandler(vertx, req, payload, sessionToken, authPosted));

					// TODO construct manifest request

					roleRequest.setChunked(true);
					roleRequest.write("");
					roleRequest.end();

					log.debug("Sent get manifest request from acl");
				}
				else {
					log.debug("Failed to fetch role.");

					ReverseProxyHandler.sendAuthError(vertx, req, res.statusCode(), data.toString("UTF-8"));
					return;
				}
			}
		});

		res.endHandler(new VoidHandler() {

			@Override
			protected void handle() {
				// TODO exit gracefully if no data has been received
			}

		});
	}
}
