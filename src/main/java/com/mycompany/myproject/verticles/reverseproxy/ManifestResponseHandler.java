package com.mycompany.myproject.verticles.reverseproxy;

import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.resourceRoot;
import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.webRoot;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import com.mycompany.myproject.verticles.filecache.FileCacheUtil;
import com.mycompany.myproject.verticles.reverseproxy.configuration.AuthConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.configuration.RewriteRule;
import com.mycompany.myproject.verticles.reverseproxy.model.SessionToken;

/**
 * @author hpark
 */
public class ManifestResponseHandler implements Handler<HttpClientResponse> {

    private static final Logger log = LoggerFactory.getLogger(ManifestResponseHandler.class);

    private final HttpServerRequest req;

    private final Vertx vertx;

    private final ReverseProxyConfiguration config;

    private final AuthConfiguration authConfig;

    private final String payload;

    private final SessionToken sessionToken;

    private final boolean authPosted;

    public ManifestResponseHandler(Vertx vertx, ReverseProxyConfiguration config, AuthConfiguration authConfig, HttpServerRequest req, String payload,
                                   SessionToken sessionToken, boolean authPosted) {
        this.req = req;
        this.vertx = vertx;
        this.config = config;
        this.authConfig = authConfig;
        this.payload = payload;
        this.sessionToken = sessionToken;
        this.authPosted = authPosted;
    }

    @Override
    public void handle(HttpClientResponse res) {

        // TODO do something with manifest ?
        if (res.statusCode() >= 200 && res.statusCode() < 300) {
            log.debug("Get manifest successful.");

            res.dataHandler(new Handler<Buffer>() {
                @Override
                public void handle(Buffer data) {
                    log.info(data.toString());
                }
            });
        } else {
            log.debug("Failed to get manifest.");
        }

        // if role request was successful, do redirect
        if (authPosted) {
            FileCacheUtil.readFile(vertx.eventBus(), log, webRoot + "redirectConfirmation.html", new RedirectHandler(vertx, req));
        } else {
            // req as uri
            URI reqURI = null;
            try {
                reqURI = new URI(req.uri());
            } catch (URISyntaxException e) {
                ReverseProxyHandler.sendFailure(req, "Bad URI: " + req.uri());
                return;
            }

            /**
             * ATTEMPT TO PARSE TARGET TOKEN FROM URL
             */
            String uriPath = reqURI.getPath().toString();
            String[] path = uriPath.split("/");
            if (path.length < 2) {
                ReverseProxyHandler.sendFailure(req, "Expected first node in URI path to be rewrite token.");
                return;
            }
            String rewriteToken = path[1];
            log.debug("Rewrite token --> " + rewriteToken);

            /**
             * LOOKUP REWRITE RULE FROM TARGET TOKEN
             */
            RewriteRule r = config.rewriteRules.get(rewriteToken);
            if (r == null) {
                ReverseProxyHandler.sendFailure(req, "Couldn't find rewrite rule for '" + rewriteToken + "'");
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
                ReverseProxyHandler.sendFailure(req, "Failed to construct URL from " + spec);
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

    }

}
