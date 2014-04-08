package com.mycompany.myproject.verticles.reverseproxy;

import static com.mycompany.myproject.verticles.reverseproxy.util.ReverseProxyUtil.isNullOrEmptyAfterTrim;

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
import com.mycompany.myproject.verticles.reverseproxy.util.ReverseProxyConstants;
import com.mycompany.myproject.verticles.reverseproxy.util.ReverseProxyUtil;

/**
 * @author robertjchristian
 */
public class AuthRequestHandler implements Handler<HttpServerRequest> {

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
					User authenticatingUser = gson.fromJson(data.toString(), User.class);
					if (authenticatingUser != null) {
						if (!isNullOrEmptyAfterTrim(authenticatingUser.getUserId()) && !isNullOrEmptyAfterTrim(authenticatingUser.getPassword())) {
							user.setUserId(authenticatingUser.getUserId());
							user.setPassword(authenticatingUser.getPassword());

							AuthRequest authRequest = new AuthRequest("NAME_PASSWORD", user.getUserId(), user.getPassword());
							AuthenticateRequest request = new AuthenticateRequest();
							request.getAuthentication().getAuthRequestList().add(authRequest);
							String authRequestStr = gson.toJson(request);
							SessionToken sessionToken = new SessionToken(user.getUserId(), null, null);

							HttpClient client = vertx.createHttpClient()
									.setHost(config.serviceDependencies.getHost("auth"))
									.setPort(config.serviceDependencies.getPort("auth"));
							final HttpClientRequest cReq = client.request("POST",
									config.serviceDependencies.getRequestPath("auth", "auth"),
									new AuthResponseHandler(vertx, config, req, key, "", sessionToken, true, null));

							cReq.setChunked(true);
							cReq.write(authRequestStr);
							cReq.end();
						}
					}
				}
			});
		}
		else {
			log.info("Received GET request to auth");

			// clear existing session cookie
			String sessionTokenCookie = ReverseProxyUtil.getCookieValue(req.headers(), ReverseProxyConstants.COOKIE_SESSION_TOKEN);
			if (sessionTokenCookie != null) {
				log.info("session-token cookie found. removing existing cookie");
				DateFormat df = new SimpleDateFormat("EEE, MMM dd yyyy hh:mm:ss zzz");
				// set to GMT
				df.setTimeZone(TimeZone.getTimeZone(""));
				req.response().headers().add("Set-Cookie", String.format("session-token=; expires=%s", df.format(new Date(0))));
			}

			// return login page
			FileCacheUtil.readFile(vertx.eventBus(), log, config.webRoot + "auth/login.html", new RedirectHandler(vertx, req));
		}
	}
}