package com.mycompany.myproject.verticles.reverseproxy;


import java.util.concurrent.ConcurrentMap;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.model.SessionToken;
import com.mycompany.myproject.verticles.reverseproxy.util.ReverseProxyUtil;

/**
 * @author hpark
 */
public class ManifestResponseHandler implements Handler<HttpClientResponse> {

	private static final Logger log = LoggerFactory.getLogger(ManifestResponseHandler.class);
	private final HttpServerRequest req;
	private final Vertx vertx;
	private final ConcurrentMap<String, byte[]> sharedCacheMap;
	private final String payload;
	private final SessionToken sessionToken;
	private final boolean authPosted;

	public ManifestResponseHandler(Vertx vertx, HttpServerRequest req, ConcurrentMap<String, byte[]> sharedCacheMap, String payload, SessionToken sessionToken,
			boolean authPosted) {
		this.req = req;
		this.vertx = vertx;
		this.sharedCacheMap = sharedCacheMap;
		this.payload = payload;
		this.sessionToken = sessionToken;
		this.authPosted = authPosted;
	}

	@Override
	public void handle(final HttpClientResponse res) {

		final ReverseProxyConfiguration config = ReverseProxyUtil.getConfig(ReverseProxyConfiguration.class,
				sharedCacheMap.get(ReverseProxyVerticle.configAfterDeployment()));
		final SecretKey key = new SecretKeySpec(sharedCacheMap.get(ReverseProxyVerticle.getResourceRoot() + config.ssl.symKeyPath), "AES");

		res.dataHandler(new Handler<Buffer>() {
			@Override
			public void handle(Buffer data) {

				final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'hh:mm:ssZ").create();

				if (res.statusCode() >= 200 && res.statusCode() < 300) {

					// TODO do something with manifest ?
					log.debug("Get manifest successful.");

					// re-assign session token
					// session timeout will be handled by UM
					byte[] encryptedSession = null;
					try {
						Cipher c = Cipher.getInstance("AES");
						c.init(Cipher.ENCRYPT_MODE, key);
						encryptedSession = c.doFinal(gson.toJson(sessionToken).getBytes("UTF-8"));
					}
					catch (Exception e) {
						ReverseProxyUtil.sendFailure(log, req, 500, "failed to encrypt session token. " + e.getMessage());
						return;
					}
					req.response().headers().add("Set-Cookie", String.format("session-token=%s", Base64.encodeBytes(encryptedSession).replace("\n", "")));

					// if manifest request was successful, do redirect
					if (authPosted) {
						ReverseProxyUtil.sendRedirect(log, req, sharedCacheMap, ReverseProxyVerticle.getWebRoot() + "redirectConfirmation.html");
					}
					else {
						// do reverse proxy
						new ReverseProxyClient().doProxy(vertx, req, payload, config, log);
					}
				}
				else {
					ReverseProxyUtil.sendFailure(log, req, res.statusCode(), data.toString());
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
