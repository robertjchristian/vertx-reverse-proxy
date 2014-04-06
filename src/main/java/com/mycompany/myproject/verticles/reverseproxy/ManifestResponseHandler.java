package com.mycompany.myproject.verticles.reverseproxy;

import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.resourceRoot;
import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.webRoot;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import com.mycompany.myproject.verticles.reverseproxy.configuration.ServiceDependencies;
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
    private final ServiceDependencies authConfig;
    private final String payload;
    private final SessionToken sessionToken;
    private final boolean authPosted;

    public ManifestResponseHandler(Vertx vertx, ReverseProxyConfiguration config, ServiceDependencies authConfig, HttpServerRequest req, String payload,
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
            // do reverse proxy
            // TODO web root should come from reverse proxy config
            String webRoot = "../../../resources/web";
            new ReverseProxyClient(webRoot).doProxy(vertx, req, payload, config, log);
        }

    }

}
