package com.mycompany.myproject.verticles.filecache;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * File cache update result
 * <p/>
 * This event should be triggered once *all* of the pending updates have been
 * assigned and subsequently processed.
 *
 * @author robertjchristian
 */
class FileCacheUpdateResult implements AsyncResult<Set<FileCacheEntry>> {

    /**
     * Log
     */
    private static final Logger log = LoggerFactory.getLogger(FileCacheUpdateResult.class);


    /**
     * Flag representing that the client has set its last pending file,
     * and that this event can be fired once pending pendingFiles list is reduced
     * to empty.  See isFinished() for additional context.
     */
    private boolean completedTaskAssignments = false;

    /**
     * Cause used in event.  Be careful here, since in N update requests,
     * there could be M errors.  May be better to manage a map of failed
     * updates (key=path, value=error), but for now leaving this as null.
     */
    private Throwable cause = null;

    /**
     * Flag for whether operation succeeded.  Client should update to true
     * if succeeded, just prior to firing event.
     */
    private boolean succeeded = false;

    /**
     * Contains a list of files that have been updated.
     */
    private Set<FileCacheEntry> updatedFilesList = new HashSet<FileCacheEntry>();

    /**
     * Contains a list of files that need update but fail.
     */
    private Set<FileCacheEntry> failedFilesList = new HashSet<FileCacheEntry>();

    /**
     * This is the set of pending files remaining to be processed.  The asynchronous handler
     * for each update is responsible for removing pending pendingFiles from this list.
     */
    private Set<String> pendingFiles = new HashSet<String>();

    /**
     * This event is ready to be fired once all assignments (all individual cache update requests)
     * have been made, and the pending list has been reduced to zero entries.
     */
    public boolean isFinished() {
        return pendingFiles.isEmpty() && hasCompletedTaskAssignments();
    }

    /**
     * Setters/Getters...
     */


    public boolean hasCompletedTaskAssignments() {
        return completedTaskAssignments;
    }

    public void setHasCompletedTaskAssignments(boolean completedTaskAssignments) {
        this.completedTaskAssignments = completedTaskAssignments;
    }

    public void addPendingFile(String file) {
        pendingFiles.add(file);
    }

    public void addUpdatedFile(FileCacheEntry file) {
        updatedFilesList.add(file);
    }

    public void addFailedFile(FileCacheEntry file) {
        failedFilesList.add(file);
    }

    public Set<FileCacheEntry> getFailedFiles() {
        return failedFilesList;
    }

    public void removePendingFile(String file) {
        pendingFiles.remove(file);
    }

    public void setSucceeded(boolean succeeded) {
        this.succeeded = succeeded;
    }

    @Override
    public boolean succeeded() {
        return succeeded;
    }

    @Override
    public boolean failed() {
        return !succeeded;
    }


    public void setCause(Throwable cause) {
        this.cause = cause;
    }

    @Override
    public Throwable cause() {
        return cause;
    }


    @Override
    public Set<FileCacheEntry> result() {
        return updatedFilesList;
    }

}

