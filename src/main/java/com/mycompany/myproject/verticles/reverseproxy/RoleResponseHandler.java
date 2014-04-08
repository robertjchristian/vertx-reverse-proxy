package com.mycompany.myproject.verticles.reverseproxy;

import javax.crypto.SecretKey;

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
import com.mycompany.myproject.verticles.reverseproxy.util.MultipartUtil;
import com.mycompany.myproject.verticles.reverseproxy.util.ReverseProxyUtil;

/**
 * @author hpark
 */
public class RoleResponseHandler implements Handler<HttpClientResponse> {

	private static final Logger log = LoggerFactory.getLogger(RoleResponseHandler.class);

	private final HttpServerRequest req;

	private final Vertx vertx;

	private final ReverseProxyConfiguration config;

	private final SecretKey key;

	private final String payload;

	private final SessionToken sessionToken;

	private final boolean authPosted;

	private final String unsignedDocument;

	private final String signedDocument;

	public RoleResponseHandler(Vertx vertx, ReverseProxyConfiguration config, HttpServerRequest req, SecretKey key, String payload, SessionToken sessionToken,
			boolean authPosted, String unsignedDocument, String signedDocument) {
		this.req = req;
		this.vertx = vertx;
		this.config = config;
		this.key = key;
		this.payload = payload;
		this.sessionToken = sessionToken;
		this.authPosted = authPosted;
		this.unsignedDocument = unsignedDocument;
		this.signedDocument = signedDocument;
	}

	@Override
	public void handle(final HttpClientResponse res) {

		final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'hh:mm:ssZ").create();

		res.dataHandler(new Handler<Buffer>() {
			@Override
			public void handle(Buffer data) {

				// role fetch successful
				if (res.statusCode() >= 200 && res.statusCode() < 300) {
					log.debug("Successfully fetched role. Getting manifest from ACL");
					HttpClient manifestClient = vertx.createHttpClient()
							.setHost(config.serviceDependencies.getHost("acl"))
							.setPort(config.serviceDependencies.getPort("acl"));
					final HttpClientRequest roleRequest = manifestClient.request("POST",
							config.serviceDependencies.getRequestPath("acl", "manifest"),
							new ManifestResponseHandler(vertx, config, req, key, payload, sessionToken, authPosted));

					String[] path = req.path().split("/");
					String manifestRequest = MultipartUtil.constructAclRequest("", gson.fromJson(data.toString(), ApplicationUser.class).getRoles());
					String multipartManifestRequest = MultipartUtil.constructManifestRequest("BaB03x", unsignedDocument, signedDocument, manifestRequest);

					roleRequest.setChunked(true);
					roleRequest.write(multipartManifestRequest);
					roleRequest.end();

					log.debug("Sent get manifest request from acl");
				}
				else {
					log.debug("Failed to fetch role.");

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
