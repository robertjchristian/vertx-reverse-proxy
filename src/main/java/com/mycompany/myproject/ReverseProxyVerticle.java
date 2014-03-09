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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;


/**
 * robertjchristian
 */
public class ReverseProxyVerticle extends Verticle {

    private static final String PROXY_TARGET_TOKEN_KEY = "PROXY_TARGET";

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

        // TODO Support rewrite rules within configuration (with host/port)

        // TODO Create IntegrationMock verticle, driven by flag
        // TODO this verticle should create target servers that
        // TODO map to corresponding mappings within configuration

        // TODO move hosts, ports, etc to configuration

        // TODO is JUL logging okay?

        // proxy server
        vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
            public void handle(final HttpServerRequest req) {

                /**
                 * Log, Get configuration, and make uri from request String
                 */

                // log
                log.info("Handling incoming proxy request: " + req.method() + " " + req.uri());
                log.debug("Headers:\n" + ReverseProxyUtil.getCookieHeadersAsJSON(req.headers()));

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
                 * Attempt to pull proxy-target-token from query string and cookie
                 */

                String proxyTargetFromQueryParam = ReverseProxyUtil.parseTokenFromQueryString(reqURI, PROXY_TARGET_TOKEN_KEY);
                log.debug("Proxy target spec from QueryString --> " + proxyTargetFromQueryParam);

                String proxyTargetFromCookie = ReverseProxyUtil.getCookieValue(req.headers(), PROXY_TARGET_TOKEN_KEY);
                log.debug("Proxy target spec from Cookie --> " + proxyTargetFromCookie);

                /**
                 * If there IS a proxy target cookie, and IS NOT a query string parameter,
                 * then redirect to include target in query string.
                 */

                if (null != proxyTargetFromCookie && null == proxyTargetFromQueryParam) {

                    log.debug("Token not found in qs, but found in cookie.");
                    config.getRewriteRules().get(proxyTargetFromCookie);

                    log.debug("Redirecting with token in qs.");

                    // use 307 (temp) instead of 301 (permanent)

                    req.response().setStatusMessage("Moved Temporarilly");
                    req.response().setStatusCode(307);
                    req.response().putHeader("Location", "http://www.foo.com/");
                    return;
                }



                if (true) {
                    req.response().setStatusMessage("Foo");
                    req.response().setStatusCode(307);
                    req.response().putHeader("Location", "http://localhost:8080/?target=google");
                    req.response().end();
                    return;
                }



                URL targetURL = null;

                try {
                    targetURL = new URL("http://www.google.com:80");
                } catch (Exception e) {
                     e.printStackTrace();
                }




                /**
                 * BEGIN REVERSE PROXYING
                 */

                final HttpClient client = vertx.createHttpClient().setHost(targetURL.getHost()).setPort(targetURL.getPort());

                final HttpClientRequest cReq = client.request(req.method(), req.uri(), new Handler<HttpClientResponse>() {
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