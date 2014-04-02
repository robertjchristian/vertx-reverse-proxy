package com.mycompany.myproject.verticles.reverseproxy;

import javax.crypto.Cipher;
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

	public AuthResponseHandler(Vertx vertx, ReverseProxyConfiguration config, HttpServerRequest req, SecretKey key, String payload, SessionToken sessionToken) {
		this.vertx = vertx;
		this.config = config;
		this.req = req;
		this.key = key;
		this.payload = payload;
		this.sessionToken = sessionToken;
	}

	@Override
	public void handle(HttpClientResponse cRes) {
		cRes.dataHandler(new Handler<Buffer>() {
			public void handle(Buffer data) {

				Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'hh:mm:ssZ").create();
				final AuthenticationResponse response = gson.fromJson(data.toString(), AuthenticationResponse.class);

				if ("success".equals(response.getResponse().getAuthentication())) {
					log.debug("authentication successful.");

					// re-assign session token
					Exception exception = null;
					byte[] encryptedSession = null;
					try {
						Cipher c = Cipher.getInstance("AES");
						c.init(Cipher.ENCRYPT_MODE, key);

						sessionToken.setAuthToken(response.getResponse().getAuthenticationToken());
						sessionToken.setSessionDate(response.getResponse().getSessionDate());
						encryptedSession = c.doFinal(gson.toJson(sessionToken).getBytes("UTF-8"));
					}
					catch (Exception e) {
						exception = e;
					}

					if (exception != null) {
						req.response().setStatusCode(500);
						req.response().setChunked(true);
						req.response().write("failed to encrypt session token.");
						req.response().end();
					}
					else {
						req.response().headers().add("Set-Cookie", String.format("session-token=%s", Base64.encodeBytes(encryptedSession).replace("\n", "")));

						log.debug("sending signPayload request to auth server");
						HttpClient signClient = vertx.createHttpClient().setHost("localhost").setPort(9000);
						final HttpClientRequest signRequest = signClient.request("POST", "/sign", new SignHandler(vertx, config, req, payload, sessionToken));

						String signRequestBody = MultipartUtil.constructSignRequest("AaB03x",
								response.getResponse().getAuthenticationToken(),
								response.getResponse().getSessionDate().toString(),
								payload);

						signRequest.setChunked(true);
						signRequest.write(signRequestBody);
						signRequest.end();

						log.debug("sent signPayload request to auth server");
					}
				}
				else {
					req.response().setStatusCode(500);
					req.response().setChunked(true);
					req.response().write("authentication failed");
					req.response().end();
				}
			}
		});
		cRes.endHandler(new VoidHandler() {
			public void handle() {
				// do nothing
			}
		});
	}
}
