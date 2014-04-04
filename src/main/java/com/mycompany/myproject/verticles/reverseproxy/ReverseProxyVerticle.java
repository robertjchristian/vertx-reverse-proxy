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
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ServiceDependencyConfiguration;

/**
 * Reverse proxy verticle
 * <p/>
 *
 * @author Robert Christian
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
	public static ReverseProxyConfiguration config;
	public static ServiceDependencyConfiguration serviceDependencyConfig;

	public static SecretKey key;

	//
	//
	// TODO A) Isolate configuration loading and updating to a utility / base class
	// TODO B) Ensure that the update response handler and http server are run by
	// TODO    the same thread. (thereby avoiding a config change in the middle of
	// TODO    processing an HTTP request)
	// TODO C) Reuse dynamic vertical config updating for other verticles
	//
	//

	/**
	 * Entry point
	 */
	public void start() {
		resourceRoot = container.config().getString("resourceRoot");
		webRoot = container.config().getString("webRoot");

		AsyncResultHandler<byte[]> handler = new AsyncResultHandler<byte[]>() {
			@Override
			public void handle(AsyncResult<byte[]> event) {
				key = new SecretKeySpec(event.result(), "AES");

				// start verticle
				doStart();
			}
		};

		FileCacheUtil.readFile(vertx.eventBus(), log, SERVICE_DEPENDENCY_CONFIG_PATH, new ConfigFileHandler<ServiceDependencyConfiguration>(vertx,
				SERVICE_DEPENDENCY_CONFIG_PATH,
				serviceDependencyConfig,
				ServiceDependencyConfiguration.class,
				null));
		FileCacheUtil.readFile(vertx.eventBus(), log, CONFIG_PATH, new ConfigFileHandler<ReverseProxyConfiguration>(vertx,
				CONFIG_PATH,
				config,
				ReverseProxyConfiguration.class,
				handler));
	}

	// called after initial filecache
	public void doStart() {

		// TODO lost ability to update dynamically... these handlers are constructed
		// once, during verticle deploy, and config is not updated

		RouteMatcher routeMatcher = new RouteMatcher();

		/**
		 * Handle requests for authentication
		 */
		routeMatcher.all("/auth", new AuthHandler(vertx));

		/**
		 * Handle requests for assets
		 */
		for (String asset : config.assets) {
			String pattern = "/.*\\." + asset;
			routeMatcher.all(pattern, new ReverseProxyHandler(vertx, false));
		}

		/**
		 * Handle all other requests
		 */
		routeMatcher.all("/.*", new ReverseProxyHandler(vertx, true));

		final HttpServer httpsServer = vertx.createHttpServer()
				.requestHandler(routeMatcher)
				.setSSL(true)
				.setKeyStorePath(resourceRoot + config.ssl.keyStorePath)
				.setKeyStorePassword(config.ssl.keyStorePassword);

		httpsServer.listen(config.ssl.proxyHttpsPort);
	}

	public static synchronized <T> void setConfig(final T newConfig) {
		if (newConfig instanceof ReverseProxyConfiguration) {
			config = (ReverseProxyConfiguration) newConfig;
		}
		else if (newConfig instanceof ServiceDependencyConfiguration) {
			serviceDependencyConfig = (ServiceDependencyConfiguration) newConfig;
		}
	}
}