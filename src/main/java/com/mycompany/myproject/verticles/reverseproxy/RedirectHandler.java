package com.mycompany.myproject.verticles.reverseproxy;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

/**
 * @author hpark
 */
public class RedirectHandler implements AsyncResultHandler<byte[]> {

    private static final Logger log = LoggerFactory.getLogger(RedirectHandler.class);

    private final Vertx vertx;
    private final HttpServerRequest req;

    public RedirectHandler(Vertx vertx, HttpServerRequest req) {
        this.vertx = vertx;
        this.req = req;
    }

    @Override
    public void handle(AsyncResult<byte[]> event) {
        try {
            // preserve original request uri for GET request
            if (req.method().equals("GET")) {
                req.response()
                        .headers()
                        .add("Set-Cookie", String.format("original-request=%s", Base64.encodeBytes(req.absoluteURI().toString().getBytes("UTF-8"))));
            }
            req.response().setChunked(true);
            req.response().setStatusCode(200);
            req.response().write(new String(event.result()));
            req.response().end();
        } catch (Exception e) {
            ReverseProxyUtil.sendFailure(log, req, e.getMessage());
        }
    }
}
