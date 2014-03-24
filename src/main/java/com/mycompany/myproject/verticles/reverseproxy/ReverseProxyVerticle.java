package com.mycompany.myproject.verticles.reverseproxy;

import com.mycompany.myproject.verticles.filecache.FileMarshalUtil;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.configuration.RewriteRule;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.*;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Verticle;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Reverse proxy verticle
 * <p/>
 *
 * @author Robert Christian
 */
public class ReverseProxyVerticle extends Verticle {

    private static final Logger log = LoggerFactory.getLogger(ReverseProxyVerticle.class);

    // static cache of configuration
    private ReverseProxyConfiguration config;

    public static final String CONFIG_PATH = "../../../conf/conf.reverseproxy.json";

    /**
     * Entry point
     */
    public void start() {
        FileMarshalUtil.readConfig(vertx.eventBus(), log, ReverseProxyConfiguration.class, CONFIG_PATH, new AsyncResultHandler<ReverseProxyConfiguration>() {
            @Override
            public void handle(AsyncResult<ReverseProxyConfiguration> event) {
                config = event.result();
                doStart();
            }
        });
    }

    // TODO container config as filecache and each verticle gets sub-config
    // ... so (eg) rewrite rules go to conf.reverse-proxy.json

    // called after initial filecache
    public void doStart() {

        final HttpServer httpsServer = vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {

            @Override
            public void handle(final HttpServerRequest req) {

                // TODO this should be sync/immediate...
                // need to test access speed (shared concurrent hash map)
                // TODO, instead of checking for updates on each request,
                // listen to event bus update...
                // TODO consider pushing config into a parent

                // always update config first
                FileMarshalUtil.readConfig(vertx.eventBus(), log, ReverseProxyConfiguration.class, CONFIG_PATH, new AsyncResultHandler<ReverseProxyConfiguration>() {
                    @Override
                    public void handle(AsyncResult<ReverseProxyConfiguration> event) {
                        config = event.result();
                        doServe(req);
                    }
                });

            }

        }).setSSL(true).setKeyStorePath(config.ssl.keyStorePath).setKeyStorePassword(config.ssl.keyStorePassword);

        httpsServer.listen(config.ssl.proxyHttpsPort);
    }

    private void doServe(final HttpServerRequest req) {

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
        } catch (URISyntaxException e) {
            sendFailure(req, "Bad URI: " + req.uri());
            return;
        }

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
        } catch (MalformedURLException e) {
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

    private void sendFailure(HttpServerRequest req, String msg) {
        log.error(msg);
        req.response().setStatusCode(500);
        req.response().setStatusMessage("Internal Server Error");
        req.response().setChunked(true);
        req.response().write(msg);
        req.response().end();
    }

}