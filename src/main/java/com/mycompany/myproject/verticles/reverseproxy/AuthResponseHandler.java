package com.mycompany.myproject.verticles.reverseproxy;

import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.webRoot;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
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
import com.mycompany.myproject.verticles.filecache.FileCacheUtil;
import com.mycompany.myproject.verticles.reverseproxy.configuration.AuthConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.model.AuthenticationResponse;
import com.mycompany.myproject.verticles.reverseproxy.model.SessionToken;

public class AuthResponseHandler implements Handler<HttpClientResponse> {

	/**
	 * Log
	 */
	private static final Logger log = LoggerFactory.getLogger(AuthResponseHandler.class);

	public static final String AUTH_SUCCESS_TEMPLATE_PATH = "auth/authSuccessful.html";
	public static final String AUTH_FAIL_NO_USER_TEMPLATE_PATH = "auth/authFailNoUserAccount.html";
	public static final String AUTH_FAIL_PASSWORD_TEMPLATE_PATH = "auth/authFailInvalidPassword.html";

	private final HttpServerRequest req;

	private final Vertx vertx;

	private final ReverseProxyConfiguration config;

	private final AuthConfiguration authConfig;

	private final SecretKey key;

	private final String payload;

	private final SessionToken sessionToken;

	private final boolean authPosted;

	public AuthResponseHandler(Vertx vertx, ReverseProxyConfiguration config, AuthConfiguration authConfig, HttpServerRequest req, SecretKey key,
			String payload, SessionToken sessionToken, boolean authPosted) {
		this.vertx = vertx;
		this.config = config;
		this.authConfig = authConfig;
		this.req = req;
		this.key = key;
		this.payload = payload;
		this.sessionToken = sessionToken;
		this.authPosted = authPosted;
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
					sessionToken.setAuthToken(response.getResponse().getAuthenticationToken());
					sessionToken.setSessionDate(response.getResponse().getSessionDate());
					Exception exception = null;
					byte[] encryptedSession = null;
					try {
						Cipher c = Cipher.getInstance("AES");
						c.init(Cipher.ENCRYPT_MODE, key);
						encryptedSession = c.doFinal(gson.toJson(sessionToken).getBytes("UTF-8"));
					}
					catch (Exception e) {
						exception = e;
					}

					if (exception != null) {
						req.response().setStatusCode(500);
						req.response().setChunked(true);
						req.response().write("failed to encrypt session token. " + exception.getMessage());
						req.response().end();
					}
					else {
						req.response().headers().add("Set-Cookie", String.format("session-token=%s", Base64.encodeBytes(encryptedSession).replace("\n", "")));

						log.debug("sending signPayload request to auth server");
						HttpClient signClient = vertx.createHttpClient().setHost(authConfig.getHost("auth")).setPort(authConfig.getPort("auth"));
						final HttpClientRequest signRequest = signClient.request("POST",
								authConfig.getRequestPath("auth", "sign"),
								new SignResponseHandler(vertx, config, authConfig, req, payload, sessionToken, authPosted));

						// TODO generate boundary
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
					log.debug("authentication failed.");

					if (data.toString().contains("No USER ACCOUNT")) {
						FileCacheUtil.readFile(vertx.eventBus(), log, webRoot + AUTH_FAIL_NO_USER_TEMPLATE_PATH, new TemplateHandler(req, 401));
					}
					else if (data.toString().contains("incorrect DN or password")) {
						FileCacheUtil.readFile(vertx.eventBus(), log, webRoot + AUTH_FAIL_PASSWORD_TEMPLATE_PATH, new TemplateHandler(req, 401));
					}
					else {
						req.response().setStatusCode(500);
						req.response().setChunked(true);
						req.response().write("authentication failed");
						req.response().end();
					}
				}
			}
		});
		cRes.endHandler(new VoidHandler() {
			public void handle() {
				// do nothing
			}
		});
	}

	private class TemplateHandler implements AsyncResultHandler<byte[]> {

		private HttpServerRequest req;
		private int statusCode;

		public TemplateHandler(HttpServerRequest req, int statusCode) {
			this.req = req;
			this.statusCode = statusCode;
		}

		@Override
		public void handle(AsyncResult<byte[]> result) {
			Buffer buffer = new Buffer(result.result());
			req.response().setStatusCode(statusCode);
			req.response().setChunked(true);
			req.response().write(buffer);
			req.response().end();
		}
	}
}
