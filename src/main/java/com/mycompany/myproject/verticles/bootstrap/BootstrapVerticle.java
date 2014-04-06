package com.mycompany.myproject.verticles.bootstrap;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.platform.Verticle;

/**
 * Main entry point for vertx-reverse-proxy
 * <p/>
 * Bootstraps proxy dependencies.
 *
 * @author robertjchristian
 */
public class BootstrapVerticle extends Verticle {

    public void start() {

        /**
         * Deploy the file cache verticle first
         */
        container.deployVerticle("com.mycompany.myproject.verticles.filecache.FileCacheVerticle", container.config(), new AsyncResultHandler<String>() {
            @Override
            public void handle(AsyncResult<String> event) {
                deployAdditionalVerticles();
            }
        });

    }

    /**
     * The file cache verticle has been deployed at this point, so modules and verticles
     * deployed from here can rely on it...
     */
    private void deployAdditionalVerticles() {
        container.deployVerticle("com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle", container.config());
    }

}