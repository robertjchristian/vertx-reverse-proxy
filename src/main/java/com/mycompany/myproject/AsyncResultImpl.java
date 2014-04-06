package com.mycompany.myproject;

import org.vertx.java.core.AsyncResult;

/**
 * Generic Asynchronous Result implementation
 * <p/>
 * This is a convenience class used to help cut down on anonymous instantiations
 * of AsyncResult, which can be verbose and plentiful when doing a lot of async.
 *
 * @author robertjchristian
 */
public class AsyncResultImpl<T> implements AsyncResult<T> {

    private final boolean succeeded;
    private final boolean failed;
    private final T result;
    private final Throwable cause;

    public AsyncResultImpl(boolean succeeded) {
        this(succeeded, null, null);
    }

    public AsyncResultImpl(boolean succeeded, T result, Throwable cause) {
        this.succeeded = succeeded;
        this.failed = !succeeded;
        this.result = result;
        this.cause = cause;
    }

    @Override
    public T result() {
        return result;
    }

    @Override
    public Throwable cause() {
        return cause;
    }

    @Override
    public boolean succeeded() {
        return succeeded;
    }

    @Override
    public boolean failed() {
        return failed;
    }

}