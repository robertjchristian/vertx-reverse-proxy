package com.mycompany.myproject.verticles.reverseproxy;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

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

import com.mycompany.myproject.verticles.filecache.FileCacheUtil;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.configuration.RewriteRule;


/**
 * Created with IntelliJ IDEA.
 * User: rob
 * Date: 3/28/14
 * Time: 11:34 AM
 * To change this template use File | Settings | File Templates.
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

	public ReverseProxyHandler(Vertx vertx, ReverseProxyConfiguration config, boolean requiresAuthAndACL) {
		this.vertx = vertx;
		this.config = config;
		this.requiresAuthAndACL = requiresAuthAndACL;
	}

	@Override
	public void handle(final HttpServerRequest req) {

		// TODO - Need to recreate session for every request.  Should guard with "requiresAuthAndACL"

		/**
		 * PARSE REQUEST
		 */
		log.info("Handling incoming proxy request:  " + req.method() + " " + req.uri());
		log.debug("Headers:  " + ReverseProxyUtil.getCookieHeadersAsJSON(req.headers()));

		if (config == null) {
			log.error("No config found.");
			sendFailure(req, "Internal Error");
			return;
		}

		// get rewrite rules as POJO
		if (config.rewriteRules == null) {
			log.error("No rewrite rules found.");
			sendFailure(req, "Internal Error");
			return;
		}

		// req as uri
		URI reqURI = null;
		try {
			reqURI = new URI(req.uri());
		}
		catch (URISyntaxException e) {
			sendFailure(req, "Bad URI: " + req.uri());
			return;
		}

		/**
		 * CHECK FOR SESSION TOKEN
		 */
		String sessionToken = ReverseProxyUtil.getCookieValue(req.headers(), "session-token");
		if (sessionToken == null || sessionToken.isEmpty()) {

			log.info("session token not found.");

			/**
			 * CHECK FOR BASIC AUTH HEADER IF SESSION TOKEN DOES NOT EXIST
			 */
			String basicAuthHeader = req.headers().get("Authorization");
			if (basicAuthHeader != null && !basicAuthHeader.isEmpty()) {
				log.info("basic auth header found.");

				HttpClient client = vertx.createHttpClient();
				// TODO use cert..? instead of trusting all
				client.setHost("localhost").setPort(config.ssl.proxyHttpsPort).setSSL(true).setTrustAll(true);
				final HttpClientRequest cReq = client.get("/auth", new Handler<HttpClientResponse>() {

					@Override
					public void handle(HttpClientResponse cRes) {
						req.response().setStatusCode(cRes.statusCode());
						req.response().headers().set(cRes.headers());
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
				req.endHandler(new VoidHandler() {
					public void handle() {
						cReq.end();
					}
				});
			}
			else {
				log.info("basic auth header not found. redirecting to login page");

				req.endHandler(new VoidHandler() {

					@Override
					protected void handle() {

						// return login page
						FileCacheUtil.readFile(vertx.eventBus(), log, "../../../src/main/resources/web/login.html", new AsyncResultHandler<byte[]>() {

							@Override
							public void handle(AsyncResult<byte[]> event) {
								try {
									// remove existing original-request cookie
									String originalRequestCookie = ReverseProxyUtil.getCookieValue(req.headers(), "original-request");
									if (originalRequestCookie != null) {
										log.info("original-request cookie found. removing existing cookie");
										DateFormat df = new SimpleDateFormat("EEE, MMM dd yyyy hh:mm:ss zzz");
										// set to GMT
										df.setTimeZone(TimeZone.getTimeZone(""));
										req.response().headers().add("Set-Cookie", String.format("original-request=; expires=%s", df.format(new Date(0))));
									}

									// preserve original request uri for GET request
									if (req.method().equals("GET")) {
										req.response()
												.headers()
												.add("Set-Cookie",
														String.format("original-request=%s", Base64.encodeBytes(req.absoluteURI().toString().getBytes("UTF-8"))));
									}
									req.response().setChunked(true);
									req.response().setStatusCode(200);
									req.response().write(new String(event.result()));
									req.response().end();
								}
								catch (Exception e) {
									sendFailure(req, e.getMessage());
								}
							}
						});
					}
				});
			}
		}
		else {
			log.info(String.format("session token found. %s", sessionToken));

			/**
			 * ATTEMPT TO PARSE TARGET TOKEN FROM URL
			 */

			String uriPath = reqURI.getPath().toString();


			String[] path = uriPath.split("/");
			if (path.length < 2) {
				sendFailure(req, "Expected first node in URI path to be rewrite token.");
				return;
			}
			String rewriteToken = path[1];
			log.debug("Rewrite token --> " + rewriteToken);

			/**
			 * LOOKUP REWRITE RULE FROM TARGET TOKEN
			 */
			RewriteRule r = config.rewriteRules.get(rewriteToken);
			if (r == null) {
				sendFailure(req, "Couldn't find rewrite rule for '" + rewriteToken + "'");
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
				sendFailure(req, "Failed to construct URL from " + spec);
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

			if (r.getProtocol().equalsIgnoreCase("https")) {
				log.debug("creating https client");
				client.setSSL(true).setTrustStorePath(config.ssl.trustStorePath).setTrustStorePassword(config.ssl.trustStorePassword);
			}

			final HttpClientRequest cReq = client.request(req.method(), targetURL.getPath().toString(), new Handler<HttpClientResponse>() {
				public void handle(HttpClientResponse cRes) {

					req.response().setStatusCode(cRes.statusCode());
					req.response().headers().set(cRes.headers());

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
			req.dataHandler(new Handler<Buffer>() {
				public void handle(Buffer data) {
					cReq.write(data);
				}
			});
			req.endHandler(new VoidHandler() {
				public void handle() {
					cReq.end();
				}
			});

		}

	}

	/**
	 * Sends a redirect to login and ends connection
	 *
	 * @param req
	 */
	private void sendAuthRedirect(final HttpServerRequest req) {
		log.info("session token not found. redirecting to login page");

		/*
		String encoded;
		try {
		    RawHttpRequest request = new RawHttpRequest(req);
		    String originalRequest = new Gson().toJson(request);
		    encoded = Base64.encodeBytes(originalRequest.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
		    encoded = "";
		}
		req.response().headers().add("Cookie", encoded);
		*/

		req.endHandler(new VoidHandler() {

			@Override
			protected void handle() {

				// return login page
				FileCacheUtil.readFile(vertx.eventBus(), log, "../../../src/main/resources/web/login.html", new AsyncResultHandler<byte[]>() {

					@Override
					public void handle(AsyncResult<byte[]> event) {
						req.response().setChunked(true);
						req.response().setStatusCode(200);
						req.response().write(new String(event.result()));
						req.response().end();
					}
				});
			}
		});

	}

	/**
	 * Sends a failure with message and ends connection.
	 *
	 * @param req - HTTP Request
	 * @param msg - Message returned to client
	 */
	private void sendFailure(HttpServerRequest req, String msg) {
		log.error(msg);
		req.response().setChunked(true);
		req.response().setStatusCode(500);
		req.response().setStatusMessage("Internal Server Error.");
		req.response().write(msg);
		req.response().end();
	}

}