package com.mycompany.myproject.verticles.reverseproxy;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;

import javax.crypto.SecretKey;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.model.AuthenticationResponse;
import com.mycompany.myproject.verticles.reverseproxy.model.SessionToken;

/**
 * @author hpark
 */
public class AuthResponseHandler implements Handler<HttpClientResponse> {

	/**
	 * Log
	 */
	private static final Logger log = LoggerFactory.getLogger(AuthResponseHandler.class);

	private final HttpServerRequest req;

	private final Vertx vertx;

	private final ReverseProxyConfiguration config;

	private final SecretKey key;

	private final String payload;

	private final SessionToken sessionToken;

	private final boolean authPosted;

	public AuthResponseHandler(Vertx vertx, ReverseProxyConfiguration config, HttpServerRequest req, SecretKey key, String payload, SessionToken sessionToken,
			boolean authPosted) {
		this.vertx = vertx;
		this.config = config;
		this.req = req;
		this.key = key;
		this.payload = payload;
		this.sessionToken = sessionToken;
		this.authPosted = authPosted;
	}

	@Override
	public void handle(final HttpClientResponse res) {
		res.dataHandler(new Handler<Buffer>() {
			public void handle(Buffer data) {

				Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'hh:mm:ssZ").create();

				if (res.statusCode() >= 200 && res.statusCode() < 300) {
					final AuthenticationResponse response = gson.fromJson(data.toString(), AuthenticationResponse.class);

					if (response != null && response.getResponse() != null) {
						if ("success".equals(response.getResponse().getAuthentication())) {
							log.debug("authentication successful.");

							// re-assign session token
							sessionToken.setAuthToken(response.getResponse().getAuthenticationToken());
							sessionToken.setSessionDate(response.getResponse().getSessionDate());

							// check payload size
							if (payload.length() > config.getMaxPayloadSizeBytesInNumber()) {
								ReverseProxyUtil.sendAuthError(log, vertx, req, 413, "Request entity too large");
								return;
							}

							// check if request is for non-default server
							// if auth reqeust has been posted, original request uri not preserved. retrieve original uri from cookie
							String uriPath;
							if (authPosted) {
								String originalRequest = ReverseProxyUtil.getCookieValue(req.headers(), "original-request");
								String uri = new String(Base64.decode(originalRequest));
								try {
									uriPath = new URI(uri).getPath();
								}
								catch (URISyntaxException e) {
									ReverseProxyUtil.sendAuthError(log, vertx, req, 500, "Bad URI: " + req.uri());
									return;
								}
							}
							else {
								uriPath = req.absoluteURI().getPath();
							}
							String[] path = uriPath.split("/");
							if (!path[1].equals(config.defaultService) && !path[1].equals("auth")) {
								// check sid
								String sid = ReverseProxyUtil.parseTokenFromQueryString(req.absoluteURI(), "sid");
								if (ReverseProxyUtil.isNullOrEmptyAfterTrim(sid)) {
									log.error("SID is required for request to non-default service");
									ReverseProxyUtil.sendAuthError(log, vertx, req, 400, "SID is required for request to non-default service");
									return;
								}
							}

							// TODO generate boundary
							String unsignedDocument = MultipartUtil.constructSignRequest("AaB03x",
									response.getResponse().getAuthenticationToken(),
									new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ").format(response.getResponse().getSessionDate()),
									payload);

							log.debug("sending signPayload request to auth server");
							HttpClient signClient = vertx.createHttpClient()
									.setHost(config.serviceDependencies.getHost("auth"))
									.setPort(config.serviceDependencies.getPort("auth"));
							final HttpClientRequest signRequest = signClient.request("POST",
									config.serviceDependencies.getRequestPath("auth", "sign"),
									new SignResponseHandler(vertx, config, req, key, payload, sessionToken, authPosted, unsignedDocument));

							signRequest.setChunked(true);
							signRequest.write(unsignedDocument);
							signRequest.end();

							log.debug("sent signPayload request to auth server");

						}
						else {
							log.debug("authentication failed.");

							if (!ReverseProxyUtil.isNullOrEmptyAfterTrim(response.getResponse().getMessage())) {
								ReverseProxyUtil.sendAuthError(log, vertx, req, 401, response.getResponse().getMessage());
								return;
							}
							else {
								ReverseProxyUtil.sendAuthError(log, vertx, req, 401, data.toString("UTF-8"));
								return;
							}
						}
					}
					else {
						log.debug("Received OK status, but did not receive any response message");

						ReverseProxyUtil.sendAuthError(log, vertx, req, 500, "Received OK status, but did not receive any response message");
						return;
					}
				}
				else {
					ReverseProxyUtil.sendAuthError(log, vertx, req, 500, data.toString("UTF-8"));
					return;
				}
			}
		});
		res.endHandler(new VoidHandler() {
			public void handle() {
				// TODO exit gracefully if no body has been received				
			}
		});
	}
}
