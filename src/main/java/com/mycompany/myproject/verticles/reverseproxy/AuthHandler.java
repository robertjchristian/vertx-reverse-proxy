package com.mycompany.myproject.verticles.reverseproxy;

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
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.model.AuthRequest;
import com.mycompany.myproject.verticles.reverseproxy.model.AuthenticateRequest;
import com.mycompany.myproject.verticles.reverseproxy.model.AuthenticationResponse;
import com.mycompany.myproject.verticles.reverseproxy.model.SessionToken;

/**
 * Created with IntelliJ IDEA.
 * User: rob
 * Date: 3/28/14
 * Time: 11:52 AM
 * To change this template use File | Settings | File Templates.
 */
public class AuthHandler implements Handler<HttpServerRequest> {

	public static final String AUTH_SUCCESS_TEMPLATE_PATH = "../../../resources/web/authSuccessful.html";
	public static final String AUTH_FAIL_NO_USER_TEMPLATE_PATH = "../../../resources/web/authFailNoUserAccount.html";
	public static final String AUTH_FAIL_PASSWORD_TEMPLATE_PATH = "../../../resources/web/authFailInvalidPassword.html";

	/**
	 * Log
	 */
	private static final Logger log = LoggerFactory.getLogger(AuthHandler.class);

	/**
	 * Configuration
	 */
	private final ReverseProxyConfiguration config;

	/**
	 * Vert.x
	 */
	private final Vertx vertx;

	/**
	 * Symmetric key
	 */
	private final SecretKey key;

	public AuthHandler(Vertx vertx, ReverseProxyConfiguration config, SecretKey key) {
		this.vertx = vertx;
		this.config = config;
		this.key = key;
	}

	@Override
	public void handle(final HttpServerRequest req) {

		final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'hh:mm:ssZ").create();

		String basicAuthHeader = req.headers().get("Authorization");
		if (basicAuthHeader != null && !basicAuthHeader.isEmpty()) {
			String authInfo = req.headers().get("Authorization");
			String parsedAuthInfo = authInfo.replace("Basic", "").trim();
			String decodedAuthInfo = new String(Base64.decode(parsedAuthInfo));
			final String[] auth = decodedAuthInfo.split(":");

			if (auth != null && auth.length == 2) {
				AuthRequest authRequest = new AuthRequest("NAME_PASSWORD", auth[0], auth[1]);
				AuthenticateRequest request = new AuthenticateRequest();
				request.getAuthentication().getAuthRequestList().add(authRequest);
				String authRequestStr = gson.toJson(request);

				HttpClient client = vertx.createHttpClient().setHost("localhost").setPort(9000);
				final HttpClientRequest cReq = client.request("POST", "/authenticate", new Handler<HttpClientResponse>() {

					@Override
					public void handle(HttpClientResponse cRes) {
						req.response().setStatusCode(cRes.statusCode());
						req.response().headers().set(cRes.headers());
						req.response().setChunked(true);

						cRes.dataHandler(new Handler<Buffer>() {
							public void handle(Buffer data) {
								AuthenticationResponse authResponse = gson.fromJson(data.toString(), AuthenticationResponse.class);
								if (authResponse != null) {
									if (authResponse.getResponse().getAuthentication().equals("success")) {

										SessionToken sessionToken = new SessionToken(auth[0],
												authResponse.getResponse().getAuthenticationToken(),
												authResponse.getResponse().getSessionDate());

										byte[] encryptedSession = null;
										try {
											Cipher c = Cipher.getInstance("AES");
											c.init(Cipher.ENCRYPT_MODE, key);
											encryptedSession = c.doFinal(gson.toJson(sessionToken).getBytes("UTF-8"));
										}
										catch (Exception e) {
											req.response().write("failed to encrypt session token.");
											req.response().end();
										}

										// base64 encoded string had newline every 60 characters(?). all cookie values must be in a single line
										req.response()
												.headers()
												.add("Set-Cookie", String.format("session-token=%s", Base64.encodeBytes(encryptedSession).replace("\n", "")));
										FileCacheUtil.readFile(vertx.eventBus(), log, AUTH_SUCCESS_TEMPLATE_PATH, new TemplateHandler(req));
									}
									else {
										if (data.toString().contains("No USER ACCOUNT")) {
											FileCacheUtil.readFile(vertx.eventBus(), log, AUTH_FAIL_NO_USER_TEMPLATE_PATH, new TemplateHandler(req));
										}
										else {
											FileCacheUtil.readFile(vertx.eventBus(), log, AUTH_FAIL_PASSWORD_TEMPLATE_PATH, new TemplateHandler(req));
										}
									}
								}
							}
						});
						cRes.endHandler(new VoidHandler() {
							public void handle() {

							}
						});
					}
				});

				cReq.setChunked(true);
				cReq.write(authRequestStr);
				cReq.end();
			}
			else {
				req.response().setStatusCode(403);
				req.response().setChunked(true);
				req.response().write("incomplete basic auth header received.");
				req.response().end();
			}
		}
		else {
			req.response().setStatusCode(403);
			req.response().setChunked(true);
			req.response().write("basic auth header not received.");
			req.response().end();
		}
	}

	private class TemplateHandler implements AsyncResultHandler<byte[]> {

		private HttpServerRequest req;

		public TemplateHandler(HttpServerRequest req) {
			this.req = req;
		}

		@Override
		public void handle(AsyncResult<byte[]> result) {
			Buffer buffer = new Buffer(result.result());
			req.response().write(buffer);
			req.response().end();
		}
	}
}