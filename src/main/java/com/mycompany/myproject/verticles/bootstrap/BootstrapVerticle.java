package com.mycompany.myproject.verticles.bootstrap;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.platform.Verticle;

/**
 * Main entry point for proxy
 * <p/>
 * Bootstraps proxy dependencies.
 *
 * @author <a href="https://github.com/robertjchristian">Robert Christian</a>
 */
public class BootstrapVerticle extends Verticle {

	public void start() {

		// deploy the file cache verticle first

		// TODO consider (1) load filecache conf, (2) deploy using file cache conf

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