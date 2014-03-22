package com.mycompany.myproject;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.impl.BaseMessage;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Verticle;

import com.google.gson.Gson;
import com.mycompany.myproject.configuration.Configuration;
import com.mycompany.myproject.configuration.RewriteRule;

/**
 * robertjchristian
 */
public class ReverseProxyVerticle extends Verticle {

	// TODO is JUL logging okay?
	private static final Logger log = LoggerFactory.getLogger(ReverseProxyVerticle.class);

	// static cache of configuration
	private Configuration config;

	private void returnFailure(HttpServerRequest req, String msg) {
		log.error(msg);
		req.response().setStatusCode(500);
		req.response().setStatusMessage("Internal Server Error");
		req.response().setChunked(true);
		req.response().write(msg);
		req.response().end();
	}

	public void readConfig(final String fileContent) {
		Gson g = new Gson();
		config = g.fromJson(fileContent, Configuration.class);
	}

	public void start() {

		// get config file path from container configuration
		final Configuration containerConfig = ReverseProxyUtil.getConfiguration(container);

		container.deployModule("com.mycompany~file-cache~1.0.0-SNAPSHOT", new Handler<AsyncResult<String>>() {
			@Override
			public void handle(AsyncResult<String> event) {
				if (event.succeeded()) {
					log.debug("publishing file registration: " + containerConfig.getConfigFilePath());
					vertx.eventBus().publish("file_registration", containerConfig.getConfigFilePath());
				}
			}
		});

		vertx.eventBus().registerHandler(containerConfig.getConfigFilePath(), new Handler<BaseMessage<String>>() {
			@Override
			public void handle(BaseMessage<String> event) {
				log.debug("received new configs for conf: \n" + event.body());
				readConfig(event.body());
			}
		});

		final HttpServer httpsServer = vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {

			@Override
			public void handle(final HttpServerRequest req) {

				/**
				 * PARSE REQUEST
				 */
				log.info("Handling incoming proxy request:  " + req.method() + " " + req.uri());
				log.debug("Headers:  " + ReverseProxyUtil.getCookieHeadersAsJSON(req.headers()));

				if (config == null) {
					log.error("No config found.");
					returnFailure(req, "Internal Error");
					return;
				}

				// get rewrite rules as POJO
				if (config.getRewriteRules() == null) {
					log.error("No rewrite rules found.");
					returnFailure(req, "Internal Error");
					return;
				}

				// req as uri
				URI reqURI = null;
				try {
					reqURI = new URI(req.uri());
				}
				catch (URISyntaxException e) {
					returnFailure(req, "Bad URI: " + req.uri());
					return;
				}

				/**
				 * ATTEMPT TO PARSE TARGET TOKEN FROM URL
				 */

				String uriPath = reqURI.getPath().toString();

				String[] path = uriPath.split("/");
				if (path.length < 2) {
					returnFailure(req, "Expected first node in URI path to be rewrite token.");
					return;
				}
				String rewriteToken = path[1];
				log.debug("Rewrite token --> " + rewriteToken);

				/**
				 * LOOKUP REWRITE RULE FROM TARGET TOKEN
				 */
				RewriteRule r = config.getRewriteRules().get(rewriteToken);
				if (r == null) {
					returnFailure(req, "Couldn't find rewrite rule for '" + rewriteToken + "'");
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
					returnFailure(req, "Failed to construct URL from " + spec);
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
					client.setSSL(true).setTrustStorePath(config.getTrustStorePath()).setTrustStorePassword(config.getTrustStorePassword());
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

		}).setSSL(true).setKeyStorePath(containerConfig.getKeyStorePath()).setKeyStorePassword(containerConfig.getKeyStorePassword());

		httpsServer.listen(containerConfig.getProxyHttpsPort());
	}
}