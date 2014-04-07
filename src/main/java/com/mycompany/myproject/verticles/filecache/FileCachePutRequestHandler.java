package com.mycompany.myproject.verticles.filecache;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

/**
 * File cache request handler
 * <p/>
 * Used by the file cache verticle to handle event-bus-driven cache messages.
 *
 * @author robertjchristian
 */
public class FileCachePutRequestHandler implements Handler<Message<String>> {

    /**
     * Log
     */
    private static final Logger log = LoggerFactory.getLogger(FileCachePutRequestHandler.class);

    /**
     * File cache reference
     */
    private final FileCacheImpl fileCache;

    public FileCachePutRequestHandler(FileCacheImpl fileCache) {
        this.fileCache = fileCache;
    }

    @Override
    /**
     * Handle put request
     */
    public void handle(final Message<String> message) {

        log.debug("FileCache received cache request on [" + message.address() + "] for [ " + message.body() + "]");

        final String path = message.body();

        /**
         * HANDLE CACHE HIT
         */
        // for now use case is:  only handle relative path
        if (fileCache.getInternalMap().get(path) != null) {
            FileCacheEntry e = fileCache.getInternalMap().get(path);
            message.reply(e.fileContents());
            return;
        }

        /**
         * HANDLE NO CACHE HIT  (first time)
         */
        // doesn't exist... call set, then return result
        // don't notify, as initial set should use callback instead of eventbus
        // however, set the update channel
        fileCache.putFile(path, FileCacheVerticle.FILE_CACHE_CHANNEL + path
                , new AsyncResultHandler<FileCacheEntry>() {
            @Override
            public void handle(AsyncResult<FileCacheEntry> event) {
                // reply to message
                if (event.succeeded()) {
                    message.reply(event.result().fileContents());
                } else {
                    log.error(event.cause());
                    // TODO consider centralizing error codes if another is needed...
                    message.fail(-1, "Failed to locate file at [" + path + "]");

                }
            }
        });

    }


}
