package com.mycompany.myproject.verticles.filecache;

/**
 * File cache entry
 *
 * @author <a href="https://github.com/robertjchristian">Robert Christian</a>
 */
public interface FileCacheEntry {

    /**
     * Last modified date of file as of last read.
     *
     */
    long lastModified();

    /**
     * Contents of file as of last read.
     */
    byte[] fileContents();

}
