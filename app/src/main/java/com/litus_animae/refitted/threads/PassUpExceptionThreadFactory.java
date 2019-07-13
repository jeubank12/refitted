package com.litus_animae.refitted.threads;

import android.support.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.concurrent.ThreadFactory;

public class PassUpExceptionThreadFactory implements ThreadFactory {

    private WeakReference<Thread.UncaughtExceptionHandler> exceptionHandler;

    public PassUpExceptionThreadFactory(Thread.UncaughtExceptionHandler exceptionHandler) {
        this.exceptionHandler = new WeakReference<>(exceptionHandler);
    }

    @Override
    public Thread newThread(@NonNull Runnable r) {
        Thread t = new Thread();
        // TODO find out if this could be a memory leak
        t.setUncaughtExceptionHandler(exceptionHandler.get());
        return t;
    }
}
