package com.mycompany.myproject.verticles.reverseproxy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Verticle;

import com.mycompany.myproject.verticles.bootstrap.BootstrapVerticle;
import com.mycompany.myproject.verticles.filecache.FileCacheVerticle;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.util.ReverseProxyUtil;

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
	public static final String CONFIG_PATH = "conf/conf.reverseproxy.json";

	/**
	 * Configuration parsed and hydrated
	 */
	private ReverseProxyConfiguration config;

	private ConcurrentMap<String, byte[]> sharedCacheMap;

	private static String resourceRoot;
	private static String webRoot;

	public static String getResourceRoot() {
		return resourceRoot;
	}

	public static String getWebRoot() {
		return webRoot;
	}

	/**
	 * Entry point
	 */
	public void start() {

		resourceRoot = container.config().getString("resourceRoot");
		webRoot = container.config().getString("webRoot");

		sharedCacheMap = vertx.sharedData().getMap(FileCacheVerticle.FILE_CACHE_MAP);
		config = ReverseProxyUtil.getConfig(ReverseProxyConfiguration.class, sharedCacheMap.get(resourceRoot + CONFIG_PATH));
		// start verticle
		doStart();
	}

	// called after initial filecache
	public void doStart() {

		// TODO lost ability to update dynamically... these handlers are constructed
		// once, during verticle deploy, and config is not updated  (need to simply reference shared map)

		RouteMatcher routeMatcher = new RouteMatcher();

		/**
		 * Handle requests for authentication
		 */
		routeMatcher.all("/auth", new AuthRequestHandler(vertx));

		/**
		 * Handle requests for assets
		 */
		for (String asset : config.assets) {
			String pattern = "/.*\\." + asset;
			log.debug("Adding asset " + pattern);
			routeMatcher.all(pattern, new ReverseProxyHandler(vertx, false));
		}

		/**
		 * Handle all other requests
		 */
		routeMatcher.all("/.*", new ReverseProxyHandler(vertx, true));

		final HttpServer httpsServer = vertx.createHttpServer()
				.requestHandler(routeMatcher)
				.setSSL(true)
				.setKeyStorePath(ReverseProxyVerticle.getResourceRoot() + config.ssl.keyStorePath)
				.setKeyStorePassword(config.ssl.keyStorePassword);

		httpsServer.listen(config.ssl.proxyHttpsPort);
	}

	public static String config() {
		return BootstrapVerticle.getResourceRoot() + CONFIG_PATH;
	}

	public static String configAfterDeployment() {
		return getResourceRoot() + CONFIG_PATH;
	}

	public static List<String> dependencies(ReverseProxyConfiguration config) {
		List<String> dependencyList = new ArrayList<String>();

		dependencyList.add(BootstrapVerticle.getWebRoot() + "auth/login.html");
		dependencyList.add(BootstrapVerticle.getWebRoot() + "redirectConfirmation.html");

		dependencyList.add(BootstrapVerticle.getResourceRoot() + config.ssl.symKeyPath);

		return dependencyList;
	}
}