package com.mycompany.myproject.verticles.filecache;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Verticle;

import java.util.Set;

/**
 * File cache verticle
 * <p/>
 * Supports asynchronous file caching and updating.  Clients access the cache using
 * the event bus protocol.
 *
 * @author robertjchristian
 */
public class FileCacheVerticle extends Verticle {

    /**
     * Log
     */
    private static final Logger log = LoggerFactory.getLogger(FileCacheVerticle.class);

    /**
     * Listens on this channel for cache requests
     *
     * Note:  This is also used as a prefix for update notifications (CHANNEL + KEY)
     */
    public static final String FILE_CACHE_CHANNEL = "file.cache.channel";

    /**
     * Name of shared cache map
     */
    public static final String FILE_CACHE_MAP = "file.cache.map";

    /**
     * Delay in milliseconds between cache updates
     */
    private static final int REFRESH_INTERVAL_MILLIS = 30 * 1000; // update every 30 seconds by default

    /**
     * Asynchronously schedule a cache update
     */
    public void scheduleUpdate(final FileCacheImpl cache, final long refreshIntervalMillis) {

        log.debug("Starting FileCacheMap update...");

        cache.updateCache(new AsyncResultHandler<Set<FileCacheEntry>>() {
            @Override
            public void handle(AsyncResult<Set<FileCacheEntry>> event) {

                // broadcast updates
                for (FileCacheEntry entry : event.result()) {

                    // TODO should be notifying on failures as well
                    vertx.eventBus().publish(entry.getEventBusNotificationChannel(), true);

                }

                log.debug("Scheduling next update in " + REFRESH_INTERVAL_MILLIS + " milliseconds");
                // scheduling this from within the timer enables us to create a window between updates,
                // as opposed to using a periodic timer, where their could be overlaps depending on how
                // long updates take...
                vertx.setTimer(refreshIntervalMillis, new Handler<Long>() {
                    @Override
                    public void handle(Long event) {
                        scheduleUpdate(cache, refreshIntervalMillis);
                    }
                });
            }
        });
    }

    /**
     * File Cache Verticle (main entry point)
     */
    public void start() {

        // set the bus reference
        final EventBus bus = vertx.eventBus();

        // set the cache reference
        final FileCacheImpl FILE_CACHE = new FileCacheImpl(this.getVertx());

        /**
         * Listen for "put" requests on event bus on channel FILE_CACHE_CHANNEL
         */
        log.debug("Registering event bus listener on [" + FILE_CACHE_CHANNEL + "] to handle file put requests.");
        bus.registerHandler(FILE_CACHE_CHANNEL, new FileCachePutRequestHandler(FILE_CACHE));

        /**
         * Kick off the updater.  The updater simply scans cache entries, and updates any entries
         * where the file on disk has been modified.
         */
        scheduleUpdate(FILE_CACHE, REFRESH_INTERVAL_MILLIS);

    }

}