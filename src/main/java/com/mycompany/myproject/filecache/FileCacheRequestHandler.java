package com.mycompany.myproject.filecache;

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
 * @author <a href="https://github.com/robertjchristian">Robert Christian</a>
 */
public class FileCacheRequestHandler implements Handler<Message<String>> {

    /**
     * Log
     */
    private static final Logger log = LoggerFactory.getLogger(FileCacheRequestHandler.class);

    /**
     * File cache reference
     */
    private final FileCache fileCache;

    public FileCacheRequestHandler(FileCache fileCache) {
        this.fileCache = fileCache;
    }

    @Override
    public void handle(final Message<String> message) {

        log.debug("FileCache received cache request on [" + message.address() + "] for [ " + message.body() + "]");

        String path = message.body();

        /**
         * HANDLE CACHE HIT
         */
        // for now use case is:  only handle relative path
        FileCacheEntry e = null;
        if (fileCache.fetchByRelativePath(path) != null) {
            e = fileCache.fetchByRelativePath(path);
            message.reply(e.fileContents());
            return;
        }

        /**
         * HANDLE NO CACHE HIT  (first time)
         */
        // doesn't exist... call set, then return result
        fileCache.setFileByRelativePath(path, new AsyncResultHandler<FileCacheEntry>() {
            @Override
            public void handle(AsyncResult<FileCacheEntry> event) {
                // reply to message
                message.reply(event.result().fileContents());
            }
        });

    }


}
