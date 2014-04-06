package com.mycompany.myproject.verticles.reverseproxy;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Verticle;

import com.google.gson.Gson;
import com.mycompany.myproject.verticles.filecache.FileCacheUtil;
import com.mycompany.myproject.verticles.filecache.FileCacheVerticle;
import com.mycompany.myproject.verticles.reverseproxy.configuration.AuthConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;

/**
 * Reverse proxy verticle
 * <p/>
 *
 * @author robertjchristian
 */
public class ReverseProxyVerticle extends Verticle {

    /**
     * Log
     */
    private static final Logger log = LoggerFactory.getLogger(ReverseProxyVerticle.class);

    /**
     * Configuration path
     */
    public static final String CONFIG_PATH = "../../../conf/conf.reverseproxy.json";
    public static final String AUTH_CONFIG_PATH = "../../../conf/conf.auth.json";

    public static String resourceRoot;
    public static String webRoot;

    /**
     * Configuration parsed and hydrated
     */
    private ReverseProxyConfiguration config;
    private AuthConfiguration authConfig;

    private SecretKey key;

    //
    //
    // TODO A) Isolate configuration loading and updating to a utility / base class
    // TODO B) Ensure that the update response handler and http server are run by
    // TODO    the same thread. (thereby avoiding a config change in the middle of
    // TODO    processing an HTTP request)
    // TODO C) Reuse dynamic vertical config updating for other verticles
    //
    //

    protected static <T> T getConfig(final Class<T> clazz, final byte[] fileContents) {
        // TODO mind encoding
        String fileAsString = new String(fileContents);

        Gson g = new Gson();
        T c = g.fromJson(fileAsString, clazz);
        return c;
    }

    /**
     * Entry point
     */
    public void start() {
        // TODO clean this up
        resourceRoot = container.config().getString("resourceRoot");
        webRoot = container.config().getString("webRoot");

        // TODO register update listener
        FileCacheUtil.readFile(vertx.eventBus(), log, AUTH_CONFIG_PATH, new AsyncResultHandler<byte[]>() {
            @Override
            public void handle(AsyncResult<byte[]> event) {
                authConfig = getConfig(AuthConfiguration.class, event.result());

            }
        });

        FileCacheUtil.readFile(vertx.eventBus(), log, CONFIG_PATH, new AsyncResultHandler<byte[]>() {
            @Override
            public void handle(AsyncResult<byte[]> event) {

                log.debug("Updating configuration based on change to [" + CONFIG_PATH + "].");

                // set configuration
                config = getConfig(ReverseProxyConfiguration.class, event.result());


                // register update listener     (TODO isolate this code for readability)
                String channel = FileCacheVerticle.FILE_CACHE_CHANNEL + CONFIG_PATH;
                vertx.eventBus().registerHandler(channel, new Handler<Message<Boolean>>() {
                    @Override
                    public void handle(Message<Boolean> message) {
                        // update config
                        log.info("Configuration file " + CONFIG_PATH + " has been updated in cache.  Re-fetching.");

                        FileCacheUtil.readFile(vertx.eventBus(), log, CONFIG_PATH, new AsyncResultHandler<byte[]>() {
                            @Override
                            public void handle(AsyncResult<byte[]> event) {

                                // set configuration
                                config = getConfig(ReverseProxyConfiguration.class, event.result());

                            }
                        });
                    }
                });

                // bootstrap key
                // TODO dynamic loading of key
                // TODO expired key handling
                FileCacheUtil.readFile(vertx.eventBus(), log, resourceRoot + config.ssl.symKeyPath, new AsyncResultHandler<byte[]>() {

                    @Override
                    public void handle(AsyncResult<byte[]> event) {
                        key = new SecretKeySpec(event.result(), "AES");

                        // start verticle
                        doStart();
                    }
                });
            }
        });
    }

    // called after initial filecache
    public void doStart() {

        // TODO lost ability to update dynamically... these handlers are constructed
        // once, during verticle deploy, and config is not updated

        RouteMatcher routeMatcher = new RouteMatcher();

        /**
         * Handle requests for authentication
         */
        routeMatcher.all("/auth", new AuthHandler(vertx, config, authConfig, key));

        /**
         * Handle requests for assets
         */
        for (String asset : config.assets) {
            String pattern = "/." + asset;
            routeMatcher.all(pattern, new ReverseProxyHandler(vertx, config, authConfig, false, key));
        }

        /**
         * Handle all other requests
         */
        routeMatcher.all("/.*", new ReverseProxyHandler(vertx, config, authConfig, true, key));

        final HttpServer httpsServer = vertx.createHttpServer()
                .requestHandler(routeMatcher)
                .setSSL(true)
                .setKeyStorePath(resourceRoot + config.ssl.keyStorePath)
                .setKeyStorePassword(config.ssl.keyStorePassword);

        httpsServer.listen(config.ssl.proxyHttpsPort);
    }


}