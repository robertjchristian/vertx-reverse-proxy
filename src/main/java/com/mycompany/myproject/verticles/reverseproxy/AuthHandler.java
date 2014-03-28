package com.mycompany.myproject.verticles.reverseproxy;

import com.mycompany.myproject.verticles.reverseproxy.model.AuthRequest;
import com.mycompany.myproject.verticles.reverseproxy.model.AuthenticateRequest;
import com.mycompany.myproject.verticles.reverseproxy.model.AuthenticationResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.impl.Base64;

import com.google.gson.Gson;
import com.mycompany.myproject.verticles.filecache.FileCacheUtil;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.configuration.RewriteRule;
import com.mycompany.myproject.verticles.reverseproxy.model.RawHttpRequest;
import org.vertx.java.core.*;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Created with IntelliJ IDEA.
 * User: rob
 * Date: 3/28/14
 * Time: 11:52 AM
 * To change this template use File | Settings | File Templates.
 */
public class AuthHandler implements Handler<HttpServerRequest> {

    /**
     * Log
     */
    private static final Logger log = LoggerFactory.getLogger(AuthHandler.class);

    /**
     * Configuration
     */
    private final ReverseProxyConfiguration config;

    /**
     * Vert.x
     */
    private final Vertx vertx;

    public AuthHandler(Vertx vertx, ReverseProxyConfiguration config) {
        this.vertx = vertx;
        this.config = config;
    }

    @Override
    public void handle(final HttpServerRequest req) {

        // TODO better error handling (what if no Authorization)
        String authInfo = req.headers().get("Authorization");
        String parsedAuthInfo = authInfo.replace("Basic", "").trim();
        String decodedAuthInfo = new String(Base64.decode(parsedAuthInfo));
        String[] auth = decodedAuthInfo.split(":");

        if (auth != null && auth.length == 2) {
            AuthRequest authRequest = new AuthRequest("NAME_PASSWORD", auth[0], auth[1]);
            AuthenticateRequest request = new AuthenticateRequest();
            request.getAuthentication().getAuthRequestList().add(authRequest);
            String authRequestStr = new Gson().toJson(request);

            // TODO read auth server host and port (and path) from config
            HttpClient client = vertx.createHttpClient().setHost("localhost").setPort(9000);
            final HttpClientRequest cReq = client.request("POST", "/authenticate", new Handler<HttpClientResponse>() {

                @Override
                public void handle(HttpClientResponse cRes) {
                    req.response().setStatusCode(cRes.statusCode());
                    req.response().headers().set(cRes.headers());

                    req.response().setChunked(true);

                    cRes.dataHandler(new Handler<Buffer>() {
                        public void handle(Buffer data) {
                            AuthenticationResponse authResponse = new Gson().fromJson(data.toString(), AuthenticationResponse.class);
                            if (authResponse != null) {
                                if (authResponse.getResponse().getAuthentication().equals("success")) {
                                    req.response()
                                            .headers()
                                            .add("Set-Cookie", String.format("session-token=%s", authResponse.getResponse().getAuthenticationToken()));
                                    // TODO serve a landing page (loaded from cache) ...
                                    // should include links to all rewrite rules
                                    req.response().write("you've reached the front page of g2 and you are authorized...");
                                } else {
                                    req.response().write(authResponse.getResponse().getMessage());
                                }
                            }
                        }
                    });
                    cRes.endHandler(new VoidHandler() {
                        public void handle() {
                            req.response().end();
                        }
                    });
                }
            });

            cReq.setChunked(true);
            cReq.write(authRequestStr);
            cReq.end();
        } else {
            req.response().setStatusCode(403);
            req.response().setChunked(true);
            req.response().write("Incomplete Basic Authorization header received.");
            req.response().end();
        }
    }
}