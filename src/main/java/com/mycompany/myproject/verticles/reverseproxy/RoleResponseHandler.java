package com.mycompany.myproject.verticles.reverseproxy;

import com.mycompany.myproject.verticles.reverseproxy.configuration.ServiceDependencies;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.model.SessionToken;

/**
 * @author hpark
 */
public class RoleResponseHandler implements Handler<HttpClientResponse> {

    private static final Logger log = LoggerFactory.getLogger(RoleResponseHandler.class);

    private final HttpServerRequest req;

    private final Vertx vertx;

    private final ReverseProxyConfiguration config;

    private final ServiceDependencies authConfig;

    private final String payload;

    private final SessionToken sessionToken;

    private final boolean authPosted;

    public RoleResponseHandler(Vertx vertx, ReverseProxyConfiguration config, ServiceDependencies authConfig, HttpServerRequest req, String payload,
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

        // role fetch successful
        if (res.statusCode() >= 200 && res.statusCode() < 300) {
            log.debug("Successfully fetched role. Getting manifest from acl service.");

            res.dataHandler(new Handler<Buffer>() {
                @Override
                public void handle(Buffer data) {
                    HttpClient signClient = vertx.createHttpClient().setHost(authConfig.getHost("acl")).setPort(authConfig.getPort("acl"));
                    final HttpClientRequest roleRequest = signClient.request("POST",
                            authConfig.getRequestPath("acl", "manifest"),
                            new ManifestResponseHandler(vertx, config, authConfig, req, payload, sessionToken, authPosted));

                    // TODO construct manifest request

                    roleRequest.setChunked(true);
                    roleRequest.write("");
                    roleRequest.end();

                    log.debug("Sent get manifest request from acl.");
                }
            });
        } else {
            log.debug("Failed to fetch role.");

            req.response().setStatusCode(res.statusCode());
            req.response().setChunked(true);
            req.response().write("Failed to fetch role.");
            req.response().end();
        }
    }
}
