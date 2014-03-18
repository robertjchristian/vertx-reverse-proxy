package com.mycompany.myproject;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.FileProps;
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
	private static Configuration config;

	private void returnFailure(HttpServerRequest req, String msg) {
		log.error(msg);
		req.response().setStatusCode(500);
		req.response().setStatusMessage("Internal Server Error");
		req.response().setChunked(true);
		req.response().write(msg);
		req.response().end();
	}

	public void readConfig(String filePath) {
		vertx.fileSystem().readFile(filePath, new AsyncResultHandler<Buffer>() {

			@Override
			public void handle(AsyncResult<Buffer> event) {
				Gson g = new Gson();
				final String rawConfig = event.result().toString();
				config = g.fromJson(rawConfig, Configuration.class);
			}

		});
	}

	public void start() {

		// get config file path from container configuration
		final Configuration containerConfig = ReverseProxyUtil.getConfiguration(container);

		// check config file every 5 seconds
		vertx.setPeriodic(5000, new Handler<Long>() {
			Date lastModified = null;

			public void handle(Long timerId) {
				vertx.fileSystem().props(containerConfig.getConfigFilePath(), new AsyncResultHandler<FileProps>() {
					public void handle(AsyncResult<FileProps> ar) {
						if (ar.succeeded()) {

							// first config file look up
							if (lastModified == null) {
								log.debug("Monitoring for first time " + ar.result().lastModifiedTime());
								lastModified = ar.result().lastModifiedTime();
								readConfig(containerConfig.getConfigFilePath());
							}
							else {
								// if config file has been modified
								if (!lastModified.equals(ar.result().lastModifiedTime())) {
									log.debug("File modified " + ar.result().lastModifiedTime());
									lastModified = ar.result().lastModifiedTime();
									readConfig(containerConfig.getConfigFilePath());
								}
								else {
									// file not modified
								}
							}
						}
						else {
							log.error("Reading File Prop Failed " + ar.cause());
						}
					}
				});
			}
		});

		final HttpServer httpServer = vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {

			@Override
			public void handle(final HttpServerRequest req) {
				log.debug("http. redirecting");

				req.response().setStatusCode(302);
				req.response().setChunked(true);
				req.response()
						.headers()
						.add("Location",
								String.format("https://%s:%d%s", req.localAddress().getHostString(), containerConfig.getProxyHttpsPort(), req.absoluteURI()
										.getPath()
										.toString()));
				req.response().end();
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

		httpServer.listen(containerConfig.getProxyHttpPort());
		httpsServer.listen(containerConfig.getProxyHttpsPort());

		// Mock ACL server
		vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {

			@Override
			public void handle(final HttpServerRequest req) {

				vertx.fileSystem().readFile("acl/manifest1.json", new AsyncResultHandler<Buffer>() {

					@Override
					public void handle(AsyncResult<Buffer> event) {
						log.debug(event.result().toString());
						req.response().setChunked(true);
						req.response().headers().add("Server", "nginx/1.4.4");
						req.response().headers().add("Date", new Date().toString());
						req.response().headers().add("Content-Type", "multipart/form-data; boundary=Boundary_3_1687687949_1394809285583");
						req.response().headers().add("MIME-Version", "1.0");
						req.response().write(event.result().toString());
						req.response().setStatusCode(200);
						req.response().end();
					}

				});
			}
		}).listen(9000);
	}
}