/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 *
 */
package com.mycompany.myproject;


import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.streams.Pump;
import org.vertx.java.platform.Verticle;

/*
 * This is a simple Java verticle which receives `ping` messages on the event bus and sends back `pong` replies
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
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
