package com.mycompany.myproject.verticles.reverseproxy;

import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyUtil.isNullOrEmptyAfterTrim;
import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.webRoot;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.crypto.SecretKey;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mycompany.myproject.verticles.filecache.FileCacheUtil;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.model.AuthRequest;
import com.mycompany.myproject.verticles.reverseproxy.model.AuthenticateRequest;
import com.mycompany.myproject.verticles.reverseproxy.model.SessionToken;
import com.mycompany.myproject.verticles.reverseproxy.model.User;

/**
 * @author robertjchristian
 */
public class AuthRequestHandler implements Handler<HttpServerRequest> {

    public static final String AUTH_SUCCESS_TEMPLATE_PATH = "auth/authSuccessful.html";
    public static final String AUTH_FAIL_NO_USER_TEMPLATE_PATH = "auth/authFailNoUserAccount.html";
    public static final String AUTH_FAIL_PASSWORD_TEMPLATE_PATH = "auth/authFailInvalidPassword.html";

    /**
     * Log
     */
    private static final Logger log = LoggerFactory.getLogger(AuthRequestHandler.class);

    /**
     * Configuration
     */
    private final ReverseProxyConfiguration config;

    /**
     * Vert.x
     */
    private final Vertx vertx;

    /**
     * Symmetric key
     */
    private final SecretKey key;

    public AuthRequestHandler(Vertx vertx, ReverseProxyConfiguration config, SecretKey key) {
        this.vertx = vertx;
        this.config = config;
        this.key = key;
    }

    @Override
    public void handle(final HttpServerRequest req) {

        final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'hh:mm:ssZ").create();

        if (req.method().equalsIgnoreCase("POST")) {
            log.info("Received POST request to auth");

            final User user = new User();
            req.dataHandler(new Handler<Buffer>() {

                @Override
                public void handle(Buffer data) {
                    User authenticatingUser = gson.fromJson(data.toString("UTF-8"), User.class);
                    if (authenticatingUser != null) {
                        if (!isNullOrEmptyAfterTrim(authenticatingUser.getUserId()) && !isNullOrEmptyAfterTrim(authenticatingUser.getPassword())) {
                            user.setUserId(authenticatingUser.getUserId());
                            user.setPassword(authenticatingUser.getPassword());

                            AuthRequest authRequest = new AuthRequest("NAME_PASSWORD", user.getUserId(), user.getPassword());
                            AuthenticateRequest request = new AuthenticateRequest();
                            request.getAuthentication().getAuthRequestList().add(authRequest);
                            String authRequestStr = gson.toJson(request);
                            SessionToken sessionToken = new SessionToken(user.getUserId(), null, null);

                            HttpClient client = vertx.createHttpClient().setHost("localhost").setPort(9000);
                            final HttpClientRequest cReq = client.request("POST", "/authenticate", new AuthResponseHandler(vertx,
                                    config,
                                    req,
                                    key,
                                    "",
                                    sessionToken,
                                    true));

                            cReq.setChunked(true);
                            cReq.write(authRequestStr);
                            cReq.end();
                        }
                    }
                }
            });
        } else {
            log.info("Received GET request to auth");

            // clear existing session cookie
            String sessionTokenCookie = ReverseProxyUtil.getCookieValue(req.headers(), "session-token");
            if (sessionTokenCookie != null) {
                log.info("session-token cookie found. removing existing cookie");
                DateFormat df = new SimpleDateFormat("EEE, MMM dd yyyy hh:mm:ss zzz");
                // set to GMT
                df.setTimeZone(TimeZone.getTimeZone(""));
                req.response().headers().add("Set-Cookie", String.format("session-token=; expires=%s", df.format(new Date(0))));
            }

            // return login page
            FileCacheUtil.readFile(vertx.eventBus(), log, webRoot + "auth/login.html", new RedirectHandler(vertx, req));
        }
    }
}