package com.mycompany.myproject.filecache;

import com.mycompany.myproject.AsyncResultImpl;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.FileProps;
import org.vertx.java.core.file.FileSystem;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * File cache
 * <p/>
 * Used by the FileCacheVerticle, this is where the meat of the cache
 * implementation is done.
 *
 * @author <a href="https://github.com/robertjchristian">Robert Christian</a>
 */
public class FileCache {

    private static final Logger log = LoggerFactory.getLogger(FileCache.class);
    private FileSystem fs;
    private String basePath;

    // the cache
    private ConcurrentMap<String, FileCacheEntry> cacheMap;

    public FileCache(FileSystem fileSystem, String basePath) {
        super();
        this.fs = fileSystem;
        this.basePath = basePath;
        this.cacheMap = new ConcurrentHashMap<String, FileCacheEntry>();
    }

    private String getAbsolutePath(String relativePath) {
        return Paths.get(basePath, relativePath).toString();
    }

    public FileCacheEntry fetchByAbsolutePath(String absolutePath) {
        return cacheMap.get(absolutePath);
    }

    public FileCacheEntry fetchByRelativePath(String relativePath) {
        return fetchByAbsolutePath(getAbsolutePath(relativePath));
    }

    // asynchronously add a file to the cache
    public void setFileByAbsolutePath(final String absolutePath, final AsyncResultHandler<FileCacheEntry> asyncResultHandler) {

        log.debug("Set file called for [" + absolutePath + "]");

        //FileCacheEntry fileCacheEntry = null;

        // read last modified date, read file, and add contents to cache
        fs.props(absolutePath, new AsyncResultHandler<FileProps>() {
            @Override
            public void handle(final AsyncResult<FileProps> result) {
                if (result.succeeded()) {

                    final long lastModified = (result.result().lastModifiedTime().getTime());

                    // read file
                    // WARN:  Bytes consumed to memory here... avoid large files
                    fs.readFile(absolutePath, new AsyncResultHandler<Buffer>() {
                        public void handle(AsyncResult<Buffer> result) {
                            if (result.succeeded()) {
                                final byte[] bytes = result.result().getBytes();

                                FileCacheEntry entry = new FileCacheEntry() {
                                    @Override
                                    public String absoluteFilepath() {
                                        return absolutePath;
                                    }

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
                                log.debug("Adding/updating file [" + absolutePath + "] to cache.  Last modified [" + entry.lastModified() + "]");
                                cacheMap.put(absolutePath, entry);

                                // return "finished success"
                                asyncResultHandler.handle(new AsyncResultImpl(true, entry, null));

                                return;

                            } else {
                                log.error("Failure loading file for cache [" + absolutePath + "]", result.cause());
                                asyncResultHandler.handle(new AsyncResultImpl(false));
                            }
                        }
                    });
                } else {
                    log.error("Failure locating file for cache [" + absolutePath + "]", result.cause());
                    // return "finished error"
                    asyncResultHandler.handle(new AsyncResultImpl(false, null, result.cause()));
                }
            }

            ;

        });
    }

    public void setFileByRelativePath(String relativePath, final AsyncResultHandler<FileCacheEntry> asyncResultHandler) {
        setFileByAbsolutePath(getAbsolutePath(relativePath), asyncResultHandler);
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
        for (final String absolutePath : cacheMap.keySet()) {

            final FileCacheEntry entry = cacheMap.get(absolutePath);

            final long currentTime = System.currentTimeMillis();


            // read last modified date, and determine whether an update is needed


            // before beginning async processing, add this file to the list
            fileCacheUpdateResult.addPendingFile(absolutePath);

            log.debug("Checking [" + absolutePath + "] for updates...");

            fs.props(absolutePath, new AsyncResultHandler<FileProps>() {
                @Override
                public void handle(final AsyncResult<FileProps> result) {
                    if (result.succeeded()) {
                        final long lastModified = (result.result().lastModifiedTime().getTime());

                        // check for file has been updated
                        if (entry.lastModified() < lastModified) {
                            log.info("Cache entry [" + absolutePath + "] has been modified.  Updating cache... ");

                            setFileByAbsolutePath(absolutePath, new AsyncResultHandler<FileCacheEntry>() {
                                @Override
                                public void handle(AsyncResult<FileCacheEntry> event) {

                                    fileCacheUpdateResult.removePendingFile(absolutePath);


                                    if (event.failed()) {
                                        log.error("Failed to update cache for [" + absolutePath + "]");
                                    }
                                }
                            });
                        } else {
                            markCompleteAndCheckFinished(fileCacheUpdateResult, updateHandler, absolutePath);

                        }

                    } else {
                        log.error("Unexpected exception determining last modified date for  [" + absolutePath + "]", result.cause());
                        markCompleteAndCheckFinished(fileCacheUpdateResult, updateHandler, absolutePath);

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

