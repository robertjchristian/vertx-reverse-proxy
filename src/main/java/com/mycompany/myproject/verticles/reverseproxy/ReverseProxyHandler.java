package com.mycompany.myproject.verticles.reverseproxy;

import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.webRoot;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mycompany.myproject.verticles.filecache.FileCacheUtil;
import com.mycompany.myproject.verticles.reverseproxy.configuration.AuthConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.model.AuthRequest;
import com.mycompany.myproject.verticles.reverseproxy.model.AuthenticateRequest;
import com.mycompany.myproject.verticles.reverseproxy.model.SessionToken;


/**
 * @author hpark
 */
public class ReverseProxyHandler implements Handler<HttpServerRequest> {

    /**
     * Log
     */
    private static final Logger log = LoggerFactory.getLogger(ReverseProxyHandler.class);

    /**
     * Configuration
     */
    private final ReverseProxyConfiguration config;

    private final AuthConfiguration authConfig;

    /**
     * Vert.x
     */
    private final Vertx vertx;

    /**
     * Requires Auth/ACL
     */
    private final boolean requiresAuthAndACL;

    /**
     *
     */
    private final SecretKey key;

    public ReverseProxyHandler(Vertx vertx, ReverseProxyConfiguration config, AuthConfiguration authConfig, boolean requiresAuthAndACL, SecretKey key) {
        this.vertx = vertx;
        this.config = config;
        this.authConfig = authConfig;
        this.requiresAuthAndACL = requiresAuthAndACL;
        this.key = key;
    }

    @Override
    public void handle(final HttpServerRequest req) {

        // TODO - Need to recreate session for every request.  Should guard with "requiresAuthAndACL"
        // done in Sign Handler

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

        // TODO consuming whole payload in memory.
        final Buffer payloadBuffer = new Buffer();
        req.dataHandler(new Handler<Buffer>() {

            @Override
            public void handle(Buffer buffer) {
                payloadBuffer.appendBuffer(buffer);
            }

        });
        req.endHandler(new VoidHandler() {

            @Override
            protected void handle() {

                Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'hh:mm:ssZ").create();
                /**
                 * CHECK FOR SESSION TOKEN
                 */
                Exception exception = null;
                SessionToken sessionToken = null;
                String authRequestStr = "";
                String sessionTokenStr = ReverseProxyUtil.getCookieValue(req.headers(), "session-token");
                String[] basicAuthHeader = ReverseProxyUtil.getAuthFromBasicAuthHeader(req.headers());

                if ((sessionTokenStr != null && !sessionTokenStr.isEmpty()) || (basicAuthHeader != null && basicAuthHeader.length == 2)) {
                    if (sessionTokenStr != null && !sessionTokenStr.isEmpty()) {
                        log.debug(String.format("Session token found. Authenticating using authentication token."));
                        byte[] decryptedSession = null;
                        try {
                            Cipher c = Cipher.getInstance("AES");
                            c.init(Cipher.DECRYPT_MODE, key);
                            decryptedSession = c.doFinal(Base64.decode(sessionTokenStr));

                            sessionToken = gson.fromJson(new String(decryptedSession), SessionToken.class);

                            AuthRequest authRequest = new AuthRequest("NAME_PASSWORD", "", "");
                            AuthenticateRequest request = new AuthenticateRequest();
                            request.setAuthenticationToken(sessionToken.getAuthToken());
                            request.getAuthentication().getAuthRequestList().add(authRequest);
                            authRequestStr = gson.toJson(request);
                        } catch (Exception e) {
                            exception = e;
                        }
                    } else {
                        log.debug("session token not found. basic auth header found.");
                        AuthRequest authRequest = new AuthRequest("NAME_PASSWORD", basicAuthHeader[0], basicAuthHeader[1]);
                        AuthenticateRequest request = new AuthenticateRequest();
                        request.getAuthentication().getAuthRequestList().add(authRequest);
                        authRequestStr = gson.toJson(request);

                        sessionToken = new SessionToken(basicAuthHeader[0], null, null);
                    }

                    if (exception == null) {
                        log.debug("Sending auth request to authentication server.");
                        HttpClient authClient = vertx.createHttpClient().setHost(authConfig.getHost("auth")).setPort(authConfig.getPort("auth"));
                        final HttpClientRequest authReq = authClient.request("POST", authConfig.getRequestPath("auth", "auth"), new AuthResponseHandler(vertx,
                                config,
                                authConfig,
                                req,
                                key,
                                payloadBuffer.toString("UTF-8"),
                                sessionToken,
                                false));

                        authReq.setChunked(true);
                        authReq.write(authRequestStr);
                        authReq.end();
                    } else {
                        req.response().setStatusCode(500);
                        req.response().setChunked(true);
                        req.response().write(exception.getMessage());
                        req.response().end();
                    }
                } else {
                    log.info("session token and basic auth header not found. redirecting to login page");

                    // return login page
                    FileCacheUtil.readFile(vertx.eventBus(), log, webRoot + "auth/login.html", new RedirectHandler(vertx, req));
                }
            }
        });
    }

    /**
     * Sends a failure with message and ends connection.
     *
     * @param req - HTTP Request
     * @param msg - Message returned to client
     */
    public static void sendFailure(final HttpServerRequest req, final String msg) {
        log.error(msg);
        req.response().setChunked(true);
        req.response().setStatusCode(500);
        req.response().setStatusMessage("Internal Server Error.");
        req.response().write(msg);
        req.response().end();
    }

}