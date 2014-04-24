package com.mycompany.myproject.verticles.bootstrap;

import java.util.concurrent.ConcurrentMap;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.platform.Verticle;

import com.mycompany.myproject.verticles.filecache.FileCacheImpl;
import com.mycompany.myproject.verticles.filecache.FileCacheVerticle;
import com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;
import com.mycompany.myproject.verticles.reverseproxy.util.ReverseProxyUtil;

/**
 * Main entry point for vertx-reverse-proxy
 * <p/>
 * Bootstraps proxy dependencies.
 *
 * @author robertjchristian
 */
public class BootstrapVerticle extends Verticle {

	private static String resourceRoot;
	private static String webRoot;

	public static String getResourceRoot() {
		return resourceRoot;
	}

	public static String getWebRoot() {
		return webRoot;
	}

	public void start() {

		resourceRoot = container.config().getString("resourceRoot");
		webRoot = container.config().getString("webRoot");

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
		FileCacheImpl fileCacheInstance = FileCacheVerticle.getFileCacheInstance(vertx);

		// load config
		String configPath = ReverseProxyVerticle.config();
		fileCacheInstance.putFileSynch(configPath, FileCacheVerticle.FILE_CACHE_CHANNEL);
		ConcurrentMap<String, byte[]> sharedCacheMap = vertx.sharedData().getMap(FileCacheVerticle.FILE_CACHE_MAP);
		ReverseProxyConfiguration config = ReverseProxyUtil.getConfig(ReverseProxyConfiguration.class, sharedCacheMap.get(configPath));

		// send config update request
		vertx.eventBus().send(FileCacheVerticle.FILE_CACHE_CHANNEL, configPath);

		// load file dependencies
		for (String path : ReverseProxyVerticle.dependencies(config)) {
			fileCacheInstance.putFileSynch(path, FileCacheVerticle.FILE_CACHE_CHANNEL);

			// send update request
			vertx.eventBus().send(FileCacheVerticle.FILE_CACHE_CHANNEL, path);
		}
		container.deployVerticle("com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle", container.config());
	}
}