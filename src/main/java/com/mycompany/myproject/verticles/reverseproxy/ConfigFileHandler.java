package com.mycompany.myproject.verticles.reverseproxy;

import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.resourceRoot;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import com.google.gson.Gson;
import com.mycompany.myproject.verticles.filecache.FileCacheUtil;
import com.mycompany.myproject.verticles.filecache.FileCacheVerticle;
import com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration;

public class ConfigFileHandler<T> implements AsyncResultHandler<byte[]> {

	private static final Logger log = LoggerFactory.getLogger(ConfigFileHandler.class);

	private Vertx vertx;
	private String configPath;
	private T config;
	private Class<T> clazz;
	private AsyncResultHandler<byte[]> handler;

	public ConfigFileHandler(Vertx vertx, String configPath, T config, Class<T> clazz, AsyncResultHandler<byte[]> handler) {
		this.vertx = vertx;
		this.configPath = configPath;
		this.config = config;
		this.clazz = clazz;
		this.handler = handler;
	}

	@Override
	public void handle(AsyncResult<byte[]> event) {
		log.debug("Updating configuration based on change to [" + configPath + "].");

		// set configuration
		config = readConfig(clazz, event.result());
		ReverseProxyVerticle.setConfig(config);

		String channel = FileCacheVerticle.FILE_CACHE_CHANNEL + configPath;
		vertx.eventBus().registerHandler(channel, new Handler<Message<Boolean>>() {
			@Override
			public void handle(Message<Boolean> message) {
				// update config
				log.info("Configuration file " + configPath + " has been updated in cache.  Re-fetching.");

				FileCacheUtil.readFile(vertx.eventBus(), log, configPath, new AsyncResultHandler<byte[]>() {
					@Override
					public void handle(AsyncResult<byte[]> event) {

						// set configuration
						config = readConfig(clazz, event.result());
						ReverseProxyVerticle.setConfig(config);
					}
				});
			}
		});

		// hack..
		if (handler != null && config instanceof ReverseProxyConfiguration) {
			ReverseProxyConfiguration proxyConfig = (ReverseProxyConfiguration) config;
			FileCacheUtil.readFile(vertx.eventBus(), log, resourceRoot + proxyConfig.ssl.symKeyPath, handler);
		}
	}

	public static <T> T readConfig(final Class<T> clazz, final byte[] fileContents) {
		// TODO mind encoding
		String fileAsString = new String(fileContents);

		Gson g = new Gson();
		T c = g.fromJson(fileAsString, clazz);
		return c;
	}
}
