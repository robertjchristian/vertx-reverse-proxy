package com.mycompany.myproject.verticles.filecache;

import com.mycompany.myproject.AsyncResultImpl;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.FileProps;
import org.vertx.java.core.file.FileSystem;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * File cache
 * <p/>
 * Core implementation.
 *
 * @author <a href="https://github.com/robertjchristian">Robert Christian</a>
 */
public class FileCacheImpl {

    private static final Logger log = LoggerFactory.getLogger(FileCacheImpl.class);
    private FileSystem fs;
    //private String basePath;

    // the cache
    private ConcurrentMap<String, FileCacheEntry> cacheMap;

    public FileCacheImpl(FileSystem fileSystem, String basePath) {
        super();
        this.fs = fileSystem;
        //this.basePath = basePath;
        this.cacheMap = new ConcurrentHashMap<String, FileCacheEntry>();
    }

    public FileCacheEntry fetch(String absolutePath) {
        return cacheMap.get(absolutePath);
    }

    // asynchronously add a file to the cache
    // if already in cache, overwrite, regardless of staleness
    public void putFile(final String path, final AsyncResultHandler<FileCacheEntry> asyncResultHandler) {

        log.debug("Set file called for [" + path + "]");


        // read last modified date, read file, and add contents to cache
        fs.props(path, new AsyncResultHandler<FileProps>() {
            @Override
            public void handle(final AsyncResult<FileProps> result) {
                if (result.succeeded()) {

                    final long lastModified = (result.result().lastModifiedTime().getTime());

                    // TODO need to check for cache hit here? (check verticle, by design)

                    // read file
                    // WARN:  Bytes consumed to memory here... avoid large files
                    fs.readFile(path, new AsyncResultHandler<Buffer>() {
                        public void handle(AsyncResult<Buffer> result) {
                            
                            if (result.succeeded()) {
                                final byte[] bytes = result.result().getBytes();

                                // TODO consider including all properties

                                FileCacheEntry entry = new FileCacheEntry() {

                                    @Override
                                    public long lastModified() {
                                        return lastModified;
                                    }

                                    @Override
                                    public byte[] fileContents() {
                                        return bytes;
                                    }
                                };

                                // put cache entry into cache map
                                log.debug("Adding/updating file [" + path + "] to cache.  Last modified [" + entry.lastModified() + "]");
                                cacheMap.put(path, entry);

                                // return "finished success"
                                asyncResultHandler.handle(new AsyncResultImpl(true, entry, null));

                                return;

                            } else {
                                log.error("Failure loading file for cache [" + path + "]", result.cause());
                                asyncResultHandler.handle(new AsyncResultImpl(false));
                            }
                        }
                    });
                } else {
                    log.error("Failure locating file for cache [" + path + "]", result.cause());
                    // return "finished error"
                    asyncResultHandler.handle(new AsyncResultImpl(false, null, result.cause()));
                }
            }

            ;

        });
    }

    // Asynchronously scans cache entries for stale files, and updates accordingly.
    // Update handler is called when last asynch process completes
    public void updateCache(final AsyncResultHandler<Integer> updateHandler) {

        // declare the result object
        final FileCacheUpdateResult fileCacheUpdateResult = new FileCacheUpdateResult();

        // handle nothing to update case
        if (cacheMap.keySet().isEmpty()) {
            fileCacheUpdateResult.setHasCompletedTaskAssignments(true);
            fileCacheUpdateResult.setSucceeded(true);
            updateHandler.handle(fileCacheUpdateResult);
            return;
        }

        // handle main case
        for (final String key : cacheMap.keySet()) {

            final FileCacheEntry entry = cacheMap.get(key);

            // before beginning async processing, add this file to the list
            fileCacheUpdateResult.addPendingFile(key);

            log.debug("Checking [" + key + "] for updates...");

            fs.props(key, new AsyncResultHandler<FileProps>() {
                @Override
                public void handle(final AsyncResult<FileProps> result) {
                    if (result.succeeded()) {
                        final long lastModified = (result.result().lastModifiedTime().getTime());

                        // check for file has been updated
                        if (entry.lastModified() < lastModified) {
                            log.info("Cache entry [" + key + "] has been modified.  Updating cache... ");

                            putFile(key, new AsyncResultHandler<FileCacheEntry>() {
                                @Override
                                public void handle(AsyncResult<FileCacheEntry> event) {

                                    if (event.failed()) {
                                        log.error("Failed to update cache for [" + key + "]");
                                    }

                                    markCompleteAndCheckFinished(fileCacheUpdateResult, updateHandler, key);

                                }
                            });
                        } else {
                            markCompleteAndCheckFinished(fileCacheUpdateResult, updateHandler, key);

                        }

                    } else {
                        log.error("Unexpected exception determining last modified date for  [" + key + "]", result.cause());
                        markCompleteAndCheckFinished(fileCacheUpdateResult, updateHandler, key);

                    }
                }
            });
        }

        // mark that task assignments have all been made
        fileCacheUpdateResult.setHasCompletedTaskAssignments(true);


    }

    public void markCompleteAndCheckFinished(FileCacheUpdateResult result, AsyncResultHandler<Integer> updateHandler, String absolutePath) {
        result.removePendingFile(absolutePath);
        if (result.isFinished()) {
            result.setSucceeded(true);
            updateHandler.handle(result);
        }
    }


}

