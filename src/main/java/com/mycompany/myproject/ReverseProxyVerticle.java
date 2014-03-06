package com.mycompany.myproject;


import com.google.gson.Gson;
import com.mycompany.myproject.configuration.Configuration;
import com.mycompany.myproject.configuration.RewriteRule;
import org.vertx.groovy.core.http.HttpServerResponse;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.platform.Verticle;

import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class ReverseProxyVerticle extends Verticle {

    private void returnFailure(HttpServerRequest req, String msg) {
        container.logger().error(msg);
        req.response().setStatusCode(500);
        req.response().setStatusMessage("Internal Server Error");
        req.response().setChunked(true);
        req.response().write(msg);
        req.response().end();
    }

    private URL findTargetURLFromAbsoluteURL(Configuration config, HttpServerRequest req) {
        try {
            // parse original path from request uri
            String rawURIPath = new URI(req.uri()).getPath().toString();

            /**
             * Parse rewrite token from URL
             */

            // Expected: host:port/rewriteToken/rest/of/target/path , where rewriteToken
            // is a moniker that maps to the target host and port... so if "google" the
            // moniker points to google.com:80, then host:port/google/foo rewrites to
            // google.com:80/foo

            String[] path = rawURIPath.split("/");
            if (path.length < 2) {
                returnFailure(req, "Expected first node in URI path to be rewrite token.");
                return null;
            }
            String rewriteToken = path[1];
            container.logger().debug("Rewrite token --> " + rewriteToken);

            /**
             * Lookup target protocol, host and port
             */
            RewriteRule r = config.getRewriteRules().get(rewriteToken);
            if (r == null) {
                returnFailure(req, "Couldn't find rewrite rule for '" + rewriteToken + "'");
                return null;
            }

            /**
             * Parse target path from URL
             */
            String targetPath = rawURIPath.substring(rewriteToken.length() + 1);
            container.logger().debug("Target path --> " + targetPath);

            /**
             * Build target URL
             */
            String queryString = new URI(req.uri()).getQuery();
            String spec = r.getProtocol() + "://" + r.getHost() + ":" + r.getPort() + targetPath;
            spec = queryString != null ? spec + "?" + queryString : spec;
            URL targetURL = new URL(spec);

            container.logger().info("Target URL --> " + targetURL.toString());

            // save rewrite token in browser cookie for subsequent
            // relative http requests

            req.response().putHeader("Set-Cookie", "proxy-token=" + rewriteToken + ";Path=/");


            return targetURL;

        } catch (Exception e) {
            returnFailure(req, e.getLocalizedMessage());
            return null;
        }
    }

    private Configuration getConfiguration() {
        // TODO want to pick these up dynamically
        Gson g = new Gson();
        final String rawConfig = container.config().toString();
        System.out.println(rawConfig);
        final Configuration config = g.fromJson(rawConfig, Configuration.class);
        return config;
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

                // get configuration as POJO
                Configuration config = getConfiguration();

                // log
                container.logger().info("Proxying request: " + req.method() + " " + req.uri());
                //System.out.println("Rewrite rules: " + config.getRewriteRules());

                // determine target url
                URL targetURL = findTargetURLFromAbsoluteURL(config, req);

                if (targetURL == null) {
                    String token = ReverseProxyUtil.getCookieValue(req, "proxy-token");

                    if (token != null) {
                        /**
                         * Lookup target protocol, host and port   TODO share
                         */
                        RewriteRule r = config.getRewriteRules().get(token);
                        if (r == null) {
                            returnFailure(req, "Couldn't find rewrite rule for '" + token + "'");
                            return;
                        }

                        /**
                         * build url  TODO share
                         */
                        try {
                            String queryString = new URI(req.uri()).getQuery();
                            String spec = r.getProtocol() + "://" + r.getHost() + ":" + r.getPort() + req.uri();
                            spec = queryString != null ? spec + "?" + queryString : spec;
                            targetURL = new URL(spec);
                        } catch (Exception e) {
                            returnFailure(req, e.getLocalizedMessage());
                            return;
                        }

                    } else {
                        returnFailure(req, "Failed to determine rewrite.");
                        return;
                    }
                }

                //final String rewriteToken =

                // debug
                //ReverseProxyUtil.printHeaders(req);

                /**
                 * Figured out target URL
                 */
                System.out.println("$$$$$$$$$$$$$$$$$$$  ---> " + targetURL);


                final HttpClient client = vertx.createHttpClient().setHost(targetURL.getHost()).setPort(targetURL.getPort());

                final HttpClientRequest cReq = client.request(req.method(), req.uri(), new Handler<HttpClientResponse>() {
                    public void handle(HttpClientResponse cRes) {

                        System.out.println("Proxying response: " + cRes.statusCode());
                        req.response().setStatusCode(cRes.statusCode());
                        req.response().headers().set(cRes.headers());

                        req.response().setChunked(true);
                        cRes.dataHandler(new Handler<Buffer>() {
                            public void handle(Buffer data) {
                                System.out.println("Proxying response body:" + data);
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
                        System.out.println("Proxying request body:" + data);
                        cReq.write(data);
                    }
                });
                req.endHandler(new VoidHandler() {
                    public void handle() {
                        System.out.println("end of the request");
                        cReq.end();
                    }
                });
            }
        }).listen(8080);

        // target server
        vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
            public void handle(final HttpServerRequest req) {
                System.out.println("Target server processing request: " + req.uri());
                req.response().setStatusCode(200);
                req.response().setChunked(true);
                req.response().write("foo");
                req.response().end();
            }
        }).listen(8282);

    }

}