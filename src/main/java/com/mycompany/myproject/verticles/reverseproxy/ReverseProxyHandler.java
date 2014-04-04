package com.mycompany.myproject.verticles.reverseproxy;

import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.config;
import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.key;
import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.resourceRoot;
import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.serviceDependencyConfig;
import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.webRoot;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.crypto.Cipher;

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
import com.mycompany.myproject.verticles.reverseproxy.configuration.RewriteRule;
import com.mycompany.myproject.verticles.reverseproxy.model.AuthRequest;
import com.mycompany.myproject.verticles.reverseproxy.model.AuthenticateRequest;
import com.mycompany.myproject.verticles.reverseproxy.model.SessionToken;


/**
 * Created with IntelliJ IDEA.
 * User: rob
 * Date: 3/28/14
 * Time: 11:34 AM
 * To change this template use File | Settings | File Templates.
 */
public class ReverseProxyHandler implements Handler<HttpServerRequest> {

	public static final String AUTH_ERROR_TEMPLATE_PATH = "auth/authError.html";

	/**
	 * Log
	 */
	private static final Logger log = LoggerFactory.getLogger(ReverseProxyHandler.class);

	/**
	 * Vert.x
	 */
	private final Vertx vertx;

	/**
	 * Requires Auth/ACL
	 */
	private final boolean requiresAuthAndACL;


	public ReverseProxyHandler(Vertx vertx, boolean requiresAuthAndACL) {
		this.vertx = vertx;
		this.requiresAuthAndACL = requiresAuthAndACL;
	}

	@Override
	public void handle(final HttpServerRequest req) {

		// TODO - Need to recreate session for every request.  Should guard with "requiresAuthAndACL"
		if (!requiresAuthAndACL) {
			ReverseProxyHandler.doReverseProxy(vertx, req, "");
			return;
		}

		/**
		 * PARSE REQUEST
		 */
		log.info("Handling incoming proxy request:  " + req.method() + " " + req.uri());
		log.debug("Headers:  " + ReverseProxyUtil.getCookieHeadersAsJSON(req.headers()));

		if (config == null) {
			log.error("No config found.");
			ReverseProxyHandler.sendAuthError(vertx, req, 500, "No config found.");
			return;
		}

		// get rewrite rules as POJO
		if (config.rewriteRules == null) {
			log.error("No rewrite rules found.");
			ReverseProxyHandler.sendAuthError(vertx, req, 500, "No rewrite rules found.");
			return;
		}

		String uriPath = req.absoluteURI().getPath().toString();
		String[] path = uriPath.split("/");
		if (path.length < 2) {
			log.info("Expected first node in URI path to be rewrite token. Redirecting to default service");
			req.response().setStatusCode(302);
			req.response().headers().add("Location", config.defaultService);
			req.response().end();
		}
		else {
			// TODO consuming whole payload in memory.
			final Buffer payloadBuffer = new Buffer();
			req.dataHandler(new Handler<Buffer>() {

				@Override
				public void handle(Buffer buffer) {
					payloadBuffer.appendBuffer(buffer);
				}

			});
			req.endHandler(new VoidHandler() {

				@Override
				protected void handle() {

					Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'hh:mm:ssZ").create();
					/**
					 * CHECK FOR SESSION TOKEN
					 */
					Exception exception = null;
					SessionToken sessionToken = null;
					String authRequestStr = "";
					String sessionTokenStr = ReverseProxyUtil.getCookieValue(req.headers(), "session-token");
					String[] basicAuthHeader = ReverseProxyUtil.getAuthFromBasicAuthHeader(req.headers());

					if ((sessionTokenStr != null && !sessionTokenStr.isEmpty()) || (basicAuthHeader != null && basicAuthHeader.length == 2)) {
						if (sessionTokenStr != null && !sessionTokenStr.isEmpty()) {
							log.debug(String.format("Session token found. Authenticating using authentication token."));
							byte[] decryptedSession = null;
							try {
								Cipher c = Cipher.getInstance("AES");
								c.init(Cipher.DECRYPT_MODE, key);
								decryptedSession = c.doFinal(Base64.decode(sessionTokenStr));

								sessionToken = gson.fromJson(new String(decryptedSession), SessionToken.class);

								AuthRequest authRequest = new AuthRequest("NAME_PASSWORD", "", "");
								AuthenticateRequest request = new AuthenticateRequest();
								request.setAuthenticationToken(sessionToken.getAuthToken());
								request.getAuthentication().getAuthRequestList().add(authRequest);
								authRequestStr = gson.toJson(request);
							}
							catch (Exception e) {
								exception = e;
							}
						}
						else {
							log.debug("session token not found. basic auth header found.");
							AuthRequest authRequest = new AuthRequest("NAME_PASSWORD", basicAuthHeader[0], basicAuthHeader[1]);
							AuthenticateRequest request = new AuthenticateRequest();
							request.getAuthentication().getAuthRequestList().add(authRequest);
							authRequestStr = gson.toJson(request);

							sessionToken = new SessionToken(basicAuthHeader[0], null, null);
						}

						if (exception == null) {
							log.debug("Sending auth request to authentication server.");
							HttpClient authClient = vertx.createHttpClient()
									.setHost(serviceDependencyConfig.getHost("auth"))
									.setPort(serviceDependencyConfig.getPort("auth"));
							final HttpClientRequest authReq = authClient.request("POST",
									serviceDependencyConfig.getRequestPath("auth", "auth"),
									new AuthResponseHandler(vertx, req, payloadBuffer.toString("UTF-8"), sessionToken, false));

							authReq.setChunked(true);
							authReq.write(authRequestStr);
							authReq.end();
						}
						else {
							log.error(exception.getMessage());
							ReverseProxyHandler.sendAuthError(vertx, req, 500, "Unable to decrypt session token: " + exception.getMessage());
							return;
						}
					}
					else {
						log.info("session token and basic auth header not found. redirecting to login page");

						// return login page
						FileCacheUtil.readFile(vertx.eventBus(), log, webRoot + "auth/login.html", new RedirectHandler(vertx, req));
						return;
					}
				}
			});
		}
	}

	public static void doReverseProxy(final Vertx vertx, final HttpServerRequest req, final String payload) {
		// req as uri
		URI reqURI = null;
		try {
			reqURI = new URI(req.uri());
		}
		catch (URISyntaxException e) {
			ReverseProxyHandler.sendAuthError(vertx, req, 500, "Bad URI: " + req.uri());
			return;
		}

		/**
		 * ATTEMPT TO PARSE TARGET TOKEN FROM URL
		 */
		String uriPath = reqURI.getPath().toString();
		String[] path = uriPath.split("/");
		if (path.length < 2) {
			ReverseProxyHandler.sendAuthError(vertx, req, 500, "Expected first node in URI path to be rewrite token.");
			return;
		}
		String rewriteToken = path[1];
		log.debug("Rewrite token --> " + rewriteToken);

		/**
		 * LOOKUP REWRITE RULE FROM TARGET TOKEN
		 */
		RewriteRule r = config.rewriteRules.get(rewriteToken);
		if (r == null) {
			ReverseProxyHandler.sendAuthError(vertx, req, 500, "Couldn't find rewrite rule for '" + rewriteToken + "'");
			return;
		}

		/**
		 * PARSE TARGET PATH FROM URL
		 */
		String targetPath = uriPath.substring(rewriteToken.length() + 1);
		log.debug("Target path --> " + targetPath);

		/**
		 * BUILD TARGET URL
		 */
		String queryString = reqURI.getQuery();
		String spec = r.getProtocol() + "://" + r.getHost() + ":" + r.getPort() + targetPath;
		spec = queryString != null ? spec + "?" + queryString : spec;
		log.debug("Constructing target URL from --> " + spec);
		URL targetURL = null;
		try {
			targetURL = new URL(spec);
		}
		catch (MalformedURLException e) {
			ReverseProxyHandler.sendAuthError(vertx, req, 500, "Failed to construct URL from " + spec);
			return;
		}

		log.info("Target URL --> " + targetURL.toString());

		/**
		 * BEGIN REVERSE PROXYING
		 */

		final HttpClient client = vertx.createHttpClient();

		log.debug("Setting host --> " + targetURL.getHost());
		client.setHost(targetURL.getHost());

		log.debug("Setting port --> " + targetURL.getPort());
		client.setPort(targetURL.getPort());

		// TODO need to be tested
		if (r.getProtocol().equalsIgnoreCase("https")) {
			log.debug("creating https client");
			client.setSSL(true);
			client.setTrustStorePath(ReverseProxyUtil.isNullOrEmptyAfterTrim(r.getTrustStorePath())
					? resourceRoot + config.ssl.trustStorePath
					: r.getTrustStorePath());
			client.setTrustStorePassword(ReverseProxyUtil.isNullOrEmptyAfterTrim(r.getTrustStorePassword())
					? config.ssl.trustStorePassword
					: r.getTrustStorePassword());
		}

		final HttpClientRequest cReq = client.request(req.method(), targetURL.getPath().toString(), new Handler<HttpClientResponse>() {
			public void handle(HttpClientResponse cRes) {

				req.response().setStatusCode(cRes.statusCode());
				req.response().headers().add(cRes.headers());
				req.response().setChunked(true);
				cRes.dataHandler(new Handler<Buffer>() {
					public void handle(Buffer data) {
						req.response().write(data);
					}
				});
				cRes.endHandler(new VoidHandler() {
					public void handle() {
						req.response().end();
					}
				});
			}
		});

		cReq.headers().set(req.headers());
		cReq.setChunked(true);
		cReq.write(payload);
		cReq.end();
	}

	public static void sendAuthError(final Vertx vertx, final HttpServerRequest req, final int statusCode, final String msg) {
		FileCacheUtil.readFile(vertx.eventBus(), log, webRoot + AUTH_ERROR_TEMPLATE_PATH, new AsyncResultHandler<byte[]>() {

			@Override
			public void handle(AsyncResult<byte[]> data) {
				String template = new String(data.result());
				String templateWithErrorMessage = template.replace("{{error}}", msg);

				req.response().setChunked(true);
				req.response().setStatusCode(statusCode);
				req.response().write(templateWithErrorMessage);
				req.response().end();
			}

		});
	}
}