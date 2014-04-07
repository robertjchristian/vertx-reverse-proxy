package com.mycompany.myproject.verticles.reverseproxy;

import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.key;
import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.webRoot;

import javax.crypto.Cipher;

import com.mycompany.myproject.verticles.reverseproxy.configuration.ServiceDependencies;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

	private final boolean authPosted;

	public ManifestResponseHandler(Vertx vertx, HttpServerRequest req, String payload, SessionToken sessionToken, boolean authPosted) {
		this.req = req;
		this.vertx = vertx;
		this.payload = payload;
		this.sessionToken = sessionToken;
		this.authPosted = authPosted;
	}

	@Override
	public void handle(final HttpClientResponse res) {

		final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'hh:mm:ssZ").create();

		res.dataHandler(new Handler<Buffer>() {
			@Override
			public void handle(Buffer data) {

				if (res.statusCode() >= 200 && res.statusCode() < 300) {

					// TODO do something with manifest ?
					log.debug("Get manifest successful.");

					// re-assign session token
					// session timeout will be handled by UM
					Exception exception = null;
					byte[] encryptedSession = null;
					try {
						Cipher c = Cipher.getInstance("AES");
						c.init(Cipher.ENCRYPT_MODE, key);
						encryptedSession = c.doFinal(gson.toJson(sessionToken).getBytes("UTF-8"));
					}
					catch (Exception e) {
						exception = e;
					}

					if (exception != null) {
						ReverseProxyHandler.sendAuthError(vertx, req, 500, "failed to encrypt session token. " + exception.getMessage());
						return;
					}
					req.response().headers().add("Set-Cookie", String.format("session-token=%s", Base64.encodeBytes(encryptedSession).replace("\n", "")));

					// if authentication POST was part of this request, do redirect
					if (authPosted) {
						FileCacheUtil.readFile(vertx.eventBus(), log, webRoot + "redirectConfirmation.html", new RedirectHandler(vertx, req));
						return;
					}

					ReverseProxyHandler.doReverseProxy(vertx, req, payload);
				}
				else {
					log.debug("Failed to get manifest.");

					ReverseProxyHandler.sendAuthError(vertx, req, res.statusCode(), data.toString("UTF-8"));
					return;
				}
			}
		});

		res.endHandler(new VoidHandler() {

			@Override
			protected void handle() {
				// TODO exit gracefully if no data has been received
			}

		});
	}
}
