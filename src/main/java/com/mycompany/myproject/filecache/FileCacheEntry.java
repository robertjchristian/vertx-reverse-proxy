package com.mycompany.myproject.filecache;

/**
 * File cache entry
 *
 * @author <a href="https://github.com/robertjchristian">Robert Christian</a>
 */
public interface FileCacheEntry {

    /**
     * Absolute file path.  This could be useful if the cache request was originally made using a relative
     * path.  This is the only way the consumer can obtain the absolute location.
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
