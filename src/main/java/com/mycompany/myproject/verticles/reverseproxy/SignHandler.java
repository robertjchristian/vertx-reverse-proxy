package com.mycompany.myproject.verticles.reverseproxy;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import com.google.gson.Gson;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.model.ApplicationUser;
import com.mycompany.myproject.verticles.reverseproxy.model.SessionToken;

public class SignHandler implements Handler<HttpClientResponse> {

	/**
	 * Log
	 */
	private static final Logger log = LoggerFactory.getLogger(SignHandler.class);

	private final HttpServerRequest req;

	private final Vertx vertx;

	private final ReverseProxyConfiguration config;

	private final String payload;

	private final SessionToken sessionToken;

	public SignHandler(Vertx vertx, ReverseProxyConfiguration config, HttpServerRequest req, String payload, SessionToken sessionToken) {
		this.vertx = vertx;
		this.config = config;
		this.req = req;
		this.payload = payload;
		this.sessionToken = sessionToken;
	}

	@Override
	public void handle(final HttpClientResponse res) {

		log.debug("Received response from auth server for sign request");

		res.dataHandler(new Handler<Buffer>() {

			@Override
			public void handle(Buffer data) {

				// payload signing successful
				if (res.statusCode() >= 200 && res.statusCode() < 300) {

					log.debug("Payload signing successful. Fetching role from engine");

					HttpClient signClient = vertx.createHttpClient().setHost("localhost").setPort(9002);
					final HttpClientRequest roleRequest = signClient.request("POST", "/roles", new RoleHandler(vertx, config, req, payload, sessionToken));

					// TODO parse the service instance id, and determine owning organization
					ApplicationUser appUser = new ApplicationUser(sessionToken.getUsername(), "organization");

					roleRequest.setChunked(true);
					roleRequest.write(new Gson().toJson(appUser));
					roleRequest.end();

				}
				// payload signing failed
				else {
					log.debug("Payload signing failed. " + data.toString());

					req.response().setStatusCode(res.statusCode());
					req.response().setChunked(true);
					req.response().write(data);
					req.response().end();
				}
			}

		});
	}
}
