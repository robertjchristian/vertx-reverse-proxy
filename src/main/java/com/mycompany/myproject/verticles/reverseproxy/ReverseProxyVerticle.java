package com.mycompany.myproject.verticles.reverseproxy;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Verticle;

import com.mycompany.myproject.verticles.filecache.FileCacheUtil;
import com.mycompany.myproject.verticles.filecache.FileCacheVerticle;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ServiceDependencyConfiguration;

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
	public static final String SERVICE_DEPENDENCY_CONFIG_PATH = "../../../conf/conf.servicedependency.json";

    public static String resourceRoot;
    public static String webRoot;

    /**
     * Configuration parsed and hydrated
     */
    private ReverseProxyConfiguration config;

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

    public ReverseProxyConfiguration getConfig() {
        return config;
    }

    /**
     * Entry point
     */
    public void start() {

        // TODO clean this up
        resourceRoot = container.config().getString("resourceRoot");
        webRoot = container.config().getString("webRoot");

        FileCacheUtil.readFile(vertx.eventBus(), log, CONFIG_PATH, new AsyncResultHandler<byte[]>() {
            @Override
            public void handle(AsyncResult<byte[]> event) {
                log.debug("Updating configuration based on change to [" + CONFIG_PATH + "].");

                // set configuration
                config = ReverseProxyUtil.getConfig(ReverseProxyConfiguration.class, event.result());

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
                                config = ReverseProxyUtil.getConfig(ReverseProxyConfiguration.class, event.result());

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
        // once, during verticle deploy, and config is not updated  (need to simply reference shared map)

        RouteMatcher routeMatcher = new RouteMatcher();

        /**
         * Handle requests for authentication
         */
        routeMatcher.all("/auth", new AuthHandler(vertx, config, key));

        /**
         * Handle requests for assets
         */
        for (String asset : config.assets) {
            String pattern = ".*" + asset;
            log.debug("Adding asset " + pattern);
            routeMatcher.all(pattern, new ReverseProxyHandler(vertx, config, false, key));
        }

        /**
         * Handle all other requests
         */
        routeMatcher.all("/.*", new ReverseProxyHandler(vertx, config, true, key));

        final HttpServer httpsServer = vertx.createHttpServer()
                .requestHandler(routeMatcher)
                .setSSL(true)
                .setKeyStorePath(resourceRoot + config.ssl.keyStorePath)
                .setKeyStorePassword(config.ssl.keyStorePassword);

        httpsServer.listen(config.ssl.proxyHttpsPort);
    }


}