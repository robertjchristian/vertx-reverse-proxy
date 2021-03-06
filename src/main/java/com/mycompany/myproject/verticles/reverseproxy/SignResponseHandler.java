package com.mycompany.myproject.verticles.reverseproxy;

import java.util.concurrent.ConcurrentMap;

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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.model.ApplicationUser;
import com.mycompany.myproject.verticles.reverseproxy.model.SessionToken;
import com.mycompany.myproject.verticles.reverseproxy.util.ReverseProxyConstants;
import com.mycompany.myproject.verticles.reverseproxy.util.ReverseProxyUtil;

/**
 * @author hpark
 */
public class SignResponseHandler implements Handler<HttpClientResponse> {

	/**
	 * Log
	 */
	private static final Logger log = LoggerFactory.getLogger(SignResponseHandler.class);

	private final HttpServerRequest req;

	private final Vertx vertx;

	private final ConcurrentMap<String, byte[]> sharedCacheMap;

	private final String payload;

	private final SessionToken sessionToken;

	private final boolean authPosted;

	private final String unsignedDocument;

	private final String refererSid;

	public SignResponseHandler(Vertx vertx, HttpServerRequest req, ConcurrentMap<String, byte[]> sharedCacheMap, String payload, SessionToken sessionToken,
			boolean authPosted, String unsignedDocument, String refererSid) {
		this.vertx = vertx;
		this.req = req;
		this.sharedCacheMap = sharedCacheMap;
		this.payload = payload;
		this.sessionToken = sessionToken;
		this.authPosted = authPosted;
		this.unsignedDocument = unsignedDocument;
		this.refererSid = refererSid;
	}

	@Override
	public void handle(final HttpClientResponse res) {

		log.debug("Received response from auth server for sign request");

		final ReverseProxyConfiguration config = ReverseProxyUtil.getConfig(ReverseProxyConfiguration.class,
				sharedCacheMap.get(ReverseProxyVerticle.configAfterDeployment()));
		final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'hh:mm:ssZ").create();

		// payload signing successful
		res.dataHandler(new Handler<Buffer>() {

			@Override
			public void handle(Buffer data) {

				if (res.statusCode() >= 200 && res.statusCode() < 300) {
					log.debug("Payload signing successful. Fetching role from engine");

					HttpClient signClient = vertx.createHttpClient()
							.setHost(config.serviceDependencies.getHost("roles"))
							.setPort(config.serviceDependencies.getPort("roles"));
					final HttpClientRequest roleRequest = signClient.request("POST",
							config.serviceDependencies.getRequestPath("roles", "roles"),
							new RoleResponseHandler(vertx, req, sharedCacheMap, payload, sessionToken, authPosted, unsignedDocument, data.toString()));

					String sid = ReverseProxyUtil.parseTokenFromQueryString(req.absoluteURI(), ReverseProxyConstants.SID);
					ApplicationUser appUser = new ApplicationUser(sessionToken.getUsername(), !ReverseProxyUtil.isNullOrEmptyAfterTrim(sid) ? sid : refererSid);

					roleRequest.setChunked(true);
					roleRequest.write(gson.toJson(appUser));
					roleRequest.end();
				}
				// payload signing failed
				else {
					log.debug("Payload signing failed.");

					ReverseProxyUtil.sendFailure(log, req, res.statusCode(), data.toString());
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
