package com.mycompany.myproject.verticles.reverseproxy;

import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.configuration.RewriteRule;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.buffer.Buffer;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;

import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.resourceRoot;

/**
 * @author robertjchristian
 */
public class ReverseProxyClient {

    private final String webRoot;

    ReverseProxyClient(String webRoot) {
        this.webRoot = webRoot;
    }

    public void doProxy(final Vertx vertx, final HttpServerRequest req, final String messageBody, final ReverseProxyConfiguration config, final Logger log) {

        // req as uri
        URI reqURI = null;
        try {
            reqURI = new URI(req.uri());
        } catch (URISyntaxException e) {
            ReverseProxyUtil.sendFailure(log, req, "Bad URI: " + req.uri());
            return;
        }

        /**
         *  HANDLE ITEMS SERVED ON SP WEB-ROOT DIRECTLY
         */

        // White-listed items served directly from the proxy
        java.util.Map<String, String> localAssets = new HashMap<>();
        localAssets.put("/favicon.ico", "image/jpg");
        localAssets.put("/css/bootstrap.min.css", "text/css");
        for (String assetPath : localAssets.keySet()) {
            if (reqURI.getPath().equals(assetPath)) {
                String path = webRoot + assetPath;
                // TODO read async, and from cache...
                Buffer b = vertx.fileSystem().readFileSync(path);
                String contentType = localAssets.get(assetPath);
                ReverseProxyUtil.send200OKResponse(log, req, b, contentType);
                return;
            }
        }

        /**
         * ATTEMPT TO PARSE TARGET TOKEN FROM URL
         */
        String uriPath = reqURI.getPath().toString();
        String[] path = uriPath.split("/");
        if (path.length < 2) {
            ReverseProxyUtil.sendFailure(log, req, "Expected first node in URI path to be rewrite token.");
            return;
        }
        String rewriteToken = path[1];
        log.debug("Rewrite token --> " + rewriteToken);

        /**
         * LOOKUP REWRITE RULE FROM TARGET TOKEN
         */
        RewriteRule r = config.rewriteRules.get(rewriteToken);
        if (r == null) {
            ReverseProxyUtil.sendFailure(log, req, "Couldn't find rewrite rule for '" + rewriteToken + "'");
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
            ReverseProxyUtil.sendFailure(log, req, "Failed to construct URL from " + spec);
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
            log.debug("Creating HTTPS client");
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

        if (null != messageBody) {
            cReq.write(messageBody);
        }

        cReq.end();
    }

}


