package com.mycompany.myproject;

import org.vertx.java.platform.Verticle;

import java.net.URI;

/**
 * robertjchristian
 */
public class BootstrapVerticle extends Verticle {

    public void start() {

        // TODO web server should use separate config
        // TODO analyze the security risk involved with using this http server
        container.deployModule("io.vertx~mod-web-server~2.1.0-SNAPSHOT", container.config());

        container.deployVerticle("com.mycompany.myproject.ReverseProxyVerticle", container.config());

    }




}