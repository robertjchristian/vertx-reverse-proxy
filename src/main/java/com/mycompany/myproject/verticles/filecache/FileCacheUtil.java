package com.mycompany.myproject.verticles.filecache;

import com.mycompany.myproject.AsyncResultImpl;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;

/**
 * FileCacheUtil
 * <p/>
 * Submits async fetch request to FileCache (may or may not incur i/o), fires event on finish,
 * and returns JSON representation of file.
 *
 * @author <a href="https://github.com/robertjchristian">Robert Christian</a>
 */
public class FileCacheUtil {

    /**
     * Read file.  On success, callback with FileCacheEntry
     *
     * @param bus
     * @param log
     * @param path
     * @param handler
     * @return
     */
    public static void readFile(final EventBus bus, final Logger log, final String path, final AsyncResultHandler<byte[]> handler) {
        log.debug("Sending event bus message on channel [" + FileCacheVerticle.FILE_CACHE_CHANNEL + "] requesting [" + path + "] from FileCache.");

        bus.send(FileCacheVerticle.FILE_CACHE_CHANNEL, path, new Handler<Message<byte[]>>() {
            @Override
            public void handle(Message<byte[]> event) {
                log.debug("Event bus replied from FileCache request for [" + path + "]");
                AsyncResult<byte[]> result = new AsyncResultImpl<byte[]>(true, event.body(), null);
                handler.handle(result);
            }
        });
    }

}