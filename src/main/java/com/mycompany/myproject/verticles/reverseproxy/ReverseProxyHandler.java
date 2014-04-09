package com.mycompany.myproject.verticles.reverseproxy;

import java.net.URI;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
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
import com.mycompany.myproject.verticles.reverseproxy.model.SessionToken;
import com.mycompany.myproject.verticles.reverseproxy.util.ReverseProxyConstants;
import com.mycompany.myproject.verticles.reverseproxy.util.ReverseProxyUtil;


/**
 * @author robertjchristian
 * @author hpark
 */
public class ReverseProxyHandler implements Handler<HttpServerRequest> {

	/**
	 * Log
	 */
	private static final Logger log = LoggerFactory.getLogger(ReverseProxyHandler.class);
	/**
	 * Configuration
	 */
	private final ReverseProxyConfiguration config;
	/**
	 * Vert.x
	 */
	private final Vertx vertx;
	/**
	 * Requires Auth/ACL
	 */
	private final boolean requiresAuthAndACL;
	/**
	 *
	 */
	private final SecretKey key;

	/**
	 * Constructor
	 */
	public ReverseProxyHandler(Vertx vertx, ReverseProxyConfiguration config, boolean requiresAuthAndACL, SecretKey key) {
		this.vertx = vertx;
		this.config = config;
		this.requiresAuthAndACL = requiresAuthAndACL;
		this.key = key;
	}

	@Override
	public void handle(final HttpServerRequest req) {

		/**
		 * PARSE REQUEST
		 */
		String sessionRequirementText = requiresAuthAndACL ? "[Session required]" : "[Session not required]";
		log.info("Handling incoming proxy request [" + req.method() + " " + req.uri() + " " + sessionRequirementText);
		log.debug("Headers:  " + ReverseProxyUtil.getCookieHeadersAsJSON(req.headers()));

		if (config == null) {
			log.error("No config found.");
			ReverseProxyUtil.sendFailure(log, req, 500, "No config found.");
			return;
		}

		// get rewrite rules as POJO
		if (config.rewriteRules == null) {
			log.error("No rewrite rules found.");
			ReverseProxyUtil.sendFailure(log, req, 500, "No rewrite rules found.");
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

					// check for sid in request
					String refererSid = null;
					if (ReverseProxyUtil.isNullOrEmptyAfterTrim(ReverseProxyUtil.parseTokenFromQueryString(req.absoluteURI(), ReverseProxyConstants.SID))) {
						// if it does not exist, then try fetching sid from referer header and pass that down the filters
						try {
							URI refererURI = new URI(req.headers().get(ReverseProxyConstants.HEADER_REFERER));
							refererSid = ReverseProxyUtil.parseTokenFromQueryString(refererURI, ReverseProxyConstants.SID);
						}
						catch (Exception e) {
							// do nothing
						}
					}


					/**
					 * If auth/acl not required (ie white-listed as an asset, reverse proxy)
					 */

					if (!requiresAuthAndACL) {
						// For assets, request header must contain either Referer and/or X-Requested-With
						// (Request with those headers are coming from browser, not from direct user input)
						if (!ReverseProxyUtil.isNullOrEmptyAfterTrim(req.headers().get(ReverseProxyConstants.HEADER_REFERER))
								|| ReverseProxyConstants.ACCEPTED_X_REQUESTED_WITH_VALUE.equals(req.headers()
										.get(ReverseProxyConstants.HEADER_X_REQUESTED_WITH))) {
							// do reverse proxy
							new ReverseProxyClient().doProxy(vertx, req, null, config, log);
							return;
						}
					}

					/**
					 * Validate session prior to reverse proxy
					 */

					Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'hh:mm:ssZ").create();

					SessionToken sessionToken = null;
					String authRequestStr = "";
					String sessionTokenStr = ReverseProxyUtil.getCookieValue(req.headers(), ReverseProxyConstants.COOKIE_SESSION_TOKEN);

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
							log.error(e.getMessage());
							ReverseProxyUtil.sendFailure(log, req, 500, "Unable to decrypt session token: " + e.getMessage());
							return;
						}

						log.debug("Sending auth request to authentication server.");
						HttpClient authClient = vertx.createHttpClient()
								.setHost(config.serviceDependencies.getHost("auth"))
								.setPort(config.serviceDependencies.getPort("auth"));
						final HttpClientRequest authReq = authClient.request("POST",
								config.serviceDependencies.getRequestPath("auth", "auth"),
								new AuthResponseHandler(vertx, config, req, key, payloadBuffer.toString(), sessionToken, false, refererSid));

						authReq.setChunked(true);
						authReq.write(authRequestStr);
						authReq.end();
					}
					else {
						log.info("session token and basic auth header not found. redirecting to login page");

						// return login page
						FileCacheUtil.readFile(vertx.eventBus(), log, config.webRoot + "auth/login.html", new RedirectHandler(vertx, req));
						return;
					}
				}
			});
		}
	}
}