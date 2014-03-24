package com.mycompany.myproject.verticles.filecache;

/**
 * File cache entry
 *
 * @author <a href="https://github.com/robertjchristian">Robert Christian</a>
 */
public interface FileCacheEntry {

    /**
     * Absolute file path.  This is also the key used to retrieve this entry from the cache.
     *
     * @return
     */
    String absoluteFilepath();

    /**
     * Last modified date of file as of last read.
     *
     * @return
     */
    long lastModified();

    /**
     * Contents of file as of last read.
     * @return
     */
    byte[] fileContents();

}
