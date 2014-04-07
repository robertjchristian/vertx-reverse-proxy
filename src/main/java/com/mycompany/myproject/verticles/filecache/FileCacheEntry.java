package com.mycompany.myproject.verticles.filecache;

import org.vertx.java.core.file.FileProps;

/**
 * File cache entry
 *
 * @author robertjchristian
 */
public interface FileCacheEntry {

    /**
     * Channel
     * <p/>
     * If non-null, broadcast updates on this channel.
     */
    String getEventBusNotificationChannel();

    /**
     * Properties associated with file on disk at time of file read
     */
    FileProps fileProps();

    /**
     * Last modified date of file as of last read.
     */
    long lastModified();

    /**
     * Contents of file as of last read.
     */
    byte[] fileContents();

}
