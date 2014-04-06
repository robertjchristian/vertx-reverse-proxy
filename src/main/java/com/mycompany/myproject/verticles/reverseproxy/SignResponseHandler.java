package com.mycompany.myproject.verticles.reverseproxy;

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

import com.google.gson.Gson;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.model.ApplicationUser;
import com.mycompany.myproject.verticles.reverseproxy.model.SessionToken;

/**
 * @author hpark
 */
public class SignResponseHandler implements Handler<HttpClientResponse> {

    /**
     * Log
     */
    private static final Logger log = LoggerFactory.getLogger(SignResponseHandler.class);

    private final HttpServerRequest req;

    private final Vertx vertx;

    private final ReverseProxyConfiguration config;

    private final ServiceDependencies authConfig;

    private final String payload;

    private final SessionToken sessionToken;

    private final boolean authPosted;

    public SignResponseHandler(Vertx vertx, ReverseProxyConfiguration config, ServiceDependencies authConfig, HttpServerRequest req, String payload,
                               SessionToken sessionToken, boolean authPosted) {
        this.vertx = vertx;
        this.config = config;
        this.authConfig = authConfig;
        this.req = req;
        this.payload = payload;
        this.sessionToken = sessionToken;
        this.authPosted = authPosted;
    }

    @Override
    public void handle(final HttpClientResponse res) {

        log.debug("Received response from auth server for sign request");

        // payload signing successful
        if (res.statusCode() >= 200 && res.statusCode() < 300) {
            log.debug("Payload signing successful. Fetching role from engine");
            res.dataHandler(new Handler<Buffer>() {

                @Override
                public void handle(Buffer data) {

                    HttpClient signClient = vertx.createHttpClient().setHost(authConfig.getHost("roles")).setPort(authConfig.getPort("roles"));
                    final HttpClientRequest roleRequest = signClient.request("POST",
                            authConfig.getRequestPath("roles", "roles"),
                            new RoleResponseHandler(vertx, config, authConfig, req, payload, sessionToken, authPosted));

                    String org = ReverseProxyUtil.parseTokenFromQueryString(req.absoluteURI(), "sid");
                    ApplicationUser appUser = new ApplicationUser(sessionToken.getUsername(), (org != null) ? org : "noOrg");

                    roleRequest.setChunked(true);
                    roleRequest.write(new Gson().toJson(appUser));
                    roleRequest.end();
                }
            });

            res.endHandler(new VoidHandler() {

                @Override
                protected void handle() {

                }

            });
        }
        // payload signing failed
        else {
            log.debug("Payload signing failed.");

            req.response().setStatusCode(res.statusCode());
            req.response().setChunked(true);
            req.response().write("Payload signing failed.");
            req.response().end();
        }
    }
}
