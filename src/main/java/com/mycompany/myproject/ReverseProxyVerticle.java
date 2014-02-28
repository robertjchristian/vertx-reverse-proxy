package com.mycompany.myproject;


import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.platform.Verticle;

public class ReverseProxyVerticle extends Verticle {

    public void start() {

        final HttpClient client = vertx.createHttpClient().setHost("localhost").setPort(8282);

        // proxy server
        vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
            public void handle(final HttpServerRequest req) {
                System.out.println("Proxying request: " + req.uri());
                final HttpClientRequest cReq = client.request(req.method(), req.uri(), new Handler<HttpClientResponse>() {
                    public void handle(HttpClientResponse cRes) {
                        System.out.println("Proxying response: " + cRes.statusCode());
                        req.response().setStatusCode(cRes.statusCode());
                        req.response().headers().set(cRes.headers());
                        req.response().setChunked(true);
                        cRes.dataHandler(new Handler<Buffer>() {
                            public void handle(Buffer data) {
                                System.out.println("Proxying response body:" + data);
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
                req.dataHandler(new Handler<Buffer>() {
                    public void handle(Buffer data) {
                        System.out.println("Proxying request body:" + data);
                        cReq.write(data);
                    }
                });
                req.endHandler(new VoidHandler() {
                    public void handle() {
                        System.out.println("end of the request");
                        cReq.end();
                    }
                });
            }
        }).listen(8080);

        // target server
        vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
            public void handle(final HttpServerRequest req) {
                System.out.println("Target server processing request: " + req.uri());
                req.response().setStatusCode(200);
                req.response().setChunked(true);
                req.response().write("foo");
                req.response().end();
            }
        }).listen(8282);

    }

}