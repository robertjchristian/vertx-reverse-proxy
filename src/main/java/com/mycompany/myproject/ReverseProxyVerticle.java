package com.mycompany.myproject;


import com.google.gson.Gson;
import com.mycompany.myproject.configuration.Configuration;
import com.mycompany.myproject.configuration.RewriteRule;

import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;

import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Verticle;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;


/**
 * robertjchristian
 */
public class ReverseProxyVerticle extends Verticle {

    // TODO is JUL logging okay?
    private static final Logger log = LoggerFactory.getLogger(ReverseProxyVerticle.class);

    private void returnFailure(HttpServerRequest req, String msg) {
        log.error(msg);
        req.response().setStatusCode(500);
        req.response().setStatusMessage("Internal Server Error");
        req.response().setChunked(true);
        req.response().write(msg);
        req.response().end();
    }

    public void start() {

        // proxy server
        vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
            public void handle(final HttpServerRequest req) {

                /**
                 * Log, get configuration, and make uri from request string
                 */

                // log
                log.info("Handling incoming proxy request:  " + req.method() + " " + req.uri());
                log.debug("Headers:  " + ReverseProxyUtil.getCookieHeadersAsJSON(req.headers()));

                // get configuration as POJO
                Configuration config = ReverseProxyUtil.getConfiguration(container);

                // req as uri
                URI reqURI = null;
                try {
                    reqURI = new URI(req.uri());
                } catch (URISyntaxException e) {
                    returnFailure(req, "Bad URI: " + req.uri());
                    return;
                }

                /**
                 * Attempt to parse proxy-target-token from context path
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
                 * Lookup rewrite rule for target protocol, host and port
                 */
                RewriteRule r = config.getRewriteRules().get(rewriteToken);
                if (r == null) {
                    returnFailure(req, "Couldn't find rewrite rule for '" + rewriteToken + "'");
                    return;
                }

                /**
                 * Parse target path from URL
                 */
                String targetPath = uriPath.substring(rewriteToken.length() + 1);
                log.debug("Target path --> " + targetPath);

                /**
                 * Build target URL
                 */
                String queryString = reqURI.getQuery();
                String spec = r.getProtocol() + "://" + r.getHost() + ":" + r.getPort() + targetPath;
                spec = queryString != null ? spec + "?" + queryString : spec;
                log.debug("Constructing target URL from --> " + spec);
                URL targetURL = null;
                try {
                    targetURL = new URL(spec);
                } catch (MalformedURLException e) {
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

                final HttpClientRequest cReq = client.request(req.method(), targetURL.getPath().toString(), new Handler<HttpClientResponse>() {
                    public void handle(HttpClientResponse cRes) {

                        //System.out.println("Proxying response: " + cRes.statusCode());
                        req.response().setStatusCode(cRes.statusCode());
                        req.response().headers().set(cRes.headers());

                        req.response().setChunked(true);
                        cRes.dataHandler(new Handler<Buffer>() {
                            public void handle(Buffer data) {
                                //System.out.println("Proxying response body:" + data);
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
                        //System.out.println("Proxying request body:" + data);
                        cReq.write(data);
                    }
                });
                req.endHandler(new VoidHandler() {
                    public void handle() {
                        //System.out.println("end of the request");
                        cReq.end();
                    }
                });
            }
        }).listen(8080);

    }

}