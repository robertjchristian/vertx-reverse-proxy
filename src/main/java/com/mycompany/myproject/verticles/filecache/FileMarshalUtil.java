package com.mycompany.myproject.verticles.filecache;

import com.google.gson.Gson;
import com.mycompany.myproject.AsyncResultImpl;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;

/**
 * FileMarshalUtil
 *
 * Submits asynch fetch request to FileCache (may or may not incur i/o), fires event on finish,
 * and returns JSON representation of file.
 *
 * @author <a href="https://github.com/robertjchristian">Robert Christian</a>
 */
public class FileMarshalUtil {

    // TODO add a readConfig that loads the file synchronously


    public static <T> void readConfig(final EventBus bus, final Logger log, final Class<T> clazz, final String path, final AsyncResultHandler<T> handler) {

        // NOTE:  This only blocks on the first hit (on startup)... once the file is in the cache,
        // subsequent requests will always return the cache entry (updates are performed in a separate thread)

        // We know by the time of sending this message that the file cache handler has been
        // fully deployed and is ready to accept this message.  See the BootstrapVerticle.
        bus.send(FileCacheVerticle.FILE_CACHE_CHANNEL, path, new Handler<Message<byte[]>>() {
            @Override
            public void handle(Message<byte[]> event) {

                log.debug("Event body:  " + event.body());
                log.debug("Retrieved file: " + path);

                byte[] fileContents = event.body();
                String fileAsString = new String(fileContents); // TODO mind encoding

                Gson g = new Gson();

                T c = g.fromJson(fileAsString, clazz);

                AsyncResult<T> result = new AsyncResultImpl<T>(true, c, null);

                handler.handle(result);
            }
        });
    }


}
