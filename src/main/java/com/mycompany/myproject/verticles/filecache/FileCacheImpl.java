package com.mycompany.myproject.verticles.filecache;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.FileProps;
import org.vertx.java.core.file.FileSystem;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import com.mycompany.myproject.AsyncResultImpl;

/**
 * File cache
 * <p/>
 * Core implementation of a file cache.
 *
 * The cache runs in a dedicated verticle, and does not need to run as a worker, since files are loaded asynchronously.
 *
 * Note that there are two methods for interacting with the cache.  The first is by messaging.  An event bus message
 * for put replies asynchronously with the result.  Additionally, the result becomes available on a shared map, and
 * updates to the map are broadcast per entry, on channel "FILE_CACHE_CHANNEL" + key.
 *
 * @author robertjchristian
 */
public class FileCacheImpl {

	/**
	 * Log
	 */
	private static final Logger log = LoggerFactory.getLogger(FileCacheImpl.class);

	/**
	 * The internal cache
	 * <p/>
	 * WARN:  Updates made to this internal map must be kept in synch
	 * with the shared map below.  Could do something like this later...
	 * http://stackoverflow.com/questions/2547754/java-propertychangesupport-for-map-or-enummap...
	 * Or more ideally, collapse these two.
	 */
	private final ConcurrentMap<String, FileCacheEntry> cacheMap;

	/**
	 * The exposed cache
	 * <p/>
	 * Shared caches cannot contain custom (eg FileCacheEntry) types, yet
	 * we need to track custom types to check for updates.  So we maintain
	 * a separate version of the cache here, for consumer verticles.
	 */
	private final ConcurrentMap<String, byte[]> sharedCacheMap;

	/**
	 * Filesystem reference
	 */
	private FileSystem fs;

	/**
	 * Constructor
	 */
	public FileCacheImpl(Vertx vertx) {
		super();
		this.fs = vertx.fileSystem();
		this.sharedCacheMap = vertx.sharedData().getMap(FileCacheVerticle.FILE_CACHE_MAP);
		this.cacheMap = new ConcurrentHashMap<>();
	}

	protected java.util.Map<String, FileCacheEntry> getInternalMap() {
		return cacheMap;
	}

	public void putFileSynch(final String path, final String updateNotificationChannel) {
		log.debug("Put file synch requested for [" + path + "]");

		final FileProps fileProps = fs.propsSync(path);
		final byte[] bytes = fs.readFileSync(path).getBytes();

		FileCacheEntry entry = new FileCacheEntry() {

			@Override
			public String getEventBusNotificationChannel() {
				return updateNotificationChannel;
			}

			@Override
			public long lastModified() {
				return fileProps.lastModifiedTime().getTime();
			}

			@Override
			public FileProps fileProps() {
				return fileProps;
			}

			@Override
			public byte[] fileContents() {
				return bytes;
			}
		};

		// put cache entry into cache map
		boolean isNew = !cacheMap.containsKey("path");

		String confirmMsg = isNew ? "Adding" : "Updating";
		confirmMsg += " file [" + path + "], modified [" + entry.lastModified() + "] to cache.";
		log.debug(confirmMsg);

		// update internal map
		cacheMap.put(path, entry);

		// update shared map
		sharedCacheMap.put(path, entry.fileContents());
	}

	/**
	 * Asynchronously add a file to the cache.  If already in cache, overwrite, regardless of staleness.
	 *
	 * @param path                      - disk location of entry
	 * @param updateNotificationChannel - subsequent updates to key will notify on this channel
	 * @param asyncResultHandler        - callback
	 */
	public void putFile(final String path, final String updateNotificationChannel, final AsyncResultHandler<FileCacheEntry> asyncResultHandler) {

		log.debug("Put file requested for [" + path + "]");

		// read last modified date, read file, and add contents to cache
		fs.props(path, new AsyncResultHandler<FileProps>() {
			@Override
			public void handle(final AsyncResult<FileProps> result) {
				if (result.succeeded()) {

					final FileProps fileProps = result.result();

					// read file
					// WARN:  Bytes consumed to memory here... avoid large files
					fs.readFile(path, new AsyncResultHandler<Buffer>() {
						public void handle(AsyncResult<Buffer> result) {

							if (result.succeeded()) {
								final byte[] bytes = result.result().getBytes();

								// TODO consider including all properties

								FileCacheEntry entry = new FileCacheEntry() {

									@Override
									public String getEventBusNotificationChannel() {
										return updateNotificationChannel;
									}

									@Override
									public long lastModified() {
										return fileProps.lastModifiedTime().getTime();
									}

									@Override
									public FileProps fileProps() {
										return fileProps;
									}

									@Override
									public byte[] fileContents() {
										return bytes;
									}
								};

								// put cache entry into cache map
								boolean isNew = !cacheMap.containsKey("path");

								String confirmMsg = isNew ? "Adding" : "Updating";
								confirmMsg += " file [" + path + "], modified [" + entry.lastModified() + "] to cache.";
								log.debug(confirmMsg);

								// update internal map
								cacheMap.put(path, entry);

								// update shared map
								sharedCacheMap.put(path, entry.fileContents());

								// return "finished success"
								asyncResultHandler.handle(new AsyncResultImpl(true, entry, null));

								return;

							}
							else {
								log.error("Failure loading file for cache [" + path + "]", result.cause());
								asyncResultHandler.handle(new AsyncResultImpl(false));
							}
						}
					});
				}
				else {
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
	public void updateCache(final AsyncResultHandler<Set<FileCacheEntry>> updateHandler) {

		// declare the result object
		final FileCacheUpdateResult fileCacheUpdateResult = new FileCacheUpdateResult();

		// handle "nothing in cache, so nothing to update" case
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

			FileCacheEntry currentEntry = cacheMap.get(key);
			final String updateNotificationChannel = (currentEntry != null && currentEntry.getEventBusNotificationChannel() != null)
					? currentEntry.getEventBusNotificationChannel()
					: null;

			fs.props(key, new AsyncResultHandler<FileProps>() {
				@Override
				public void handle(final AsyncResult<FileProps> result) {
					if (result.succeeded()) {
						final long lastModified = (result.result().lastModifiedTime().getTime());

						// check for file has been updatedFilesList
						if (entry.lastModified() < lastModified) {
							log.info("Cache entry [" + key + "] has been modified.  Updating cache... ");

							putFile(key, updateNotificationChannel, new AsyncResultHandler<FileCacheEntry>() {
								@Override
								public void handle(AsyncResult<FileCacheEntry> event) {
									if (event.failed()) {
										log.error("Failed to update cache for [" + key + "]");
									}
									else {
										// should only "succeed" if successfully updated cache
										if (event.succeeded()) {
											// add to the list of "updated files" so that
											// updates can be broadcast downstream
											fileCacheUpdateResult.addUpdatedFile(event.result());
										}
										else {
											fileCacheUpdateResult.addFailedFile(event.result());
										}
									}
									markCompleteAndCheckFinished(fileCacheUpdateResult, updateHandler, key);
								}
							});
						}
						else {
							markCompleteAndCheckFinished(fileCacheUpdateResult, updateHandler, key);
						}
					}
					else {
						log.error("Unexpected exception determining last modified date for  [" + key + "]", result.cause());
						markCompleteAndCheckFinished(fileCacheUpdateResult, updateHandler, key);
					}
				}
			});
		}

		// mark that task assignments have all been made
		fileCacheUpdateResult.setHasCompletedTaskAssignments(true);

	}

	public void markCompleteAndCheckFinished(FileCacheUpdateResult result, AsyncResultHandler<Set<FileCacheEntry>> updateHandler, String absolutePath) {
		result.removePendingFile(absolutePath);
		if (result.isFinished()) {
			result.setSucceeded(true);
			updateHandler.handle(result);
		}
	}

}
