/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */
package org.glassfish.enterprise.concurrent.virtualthreads;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.glassfish.enterprise.concurrent.AbstractManagedExecutorService;
import org.glassfish.enterprise.concurrent.internal.ManagedFutureTask;

/**
 * A ManagedFutureTask for virtual threads executors. Automatically triggeres events before and after executing the
 * task. Implements pooling via provided semaphores.
 */
public class VirtualThreadsManagedFutureTask<V> extends ManagedFutureTask<V> {

    private Semaphore parallelTasksSemaphore = null;

    private Runnable taskCompletionHandler = null;

    public VirtualThreadsManagedFutureTask(AbstractManagedExecutorService executor, Runnable runnable, V result, Semaphore parallelTasksSemaphore, Runnable taskCompletionHandler) {
        super(executor, runnable, result);
        this.parallelTasksSemaphore = parallelTasksSemaphore;
        this.taskCompletionHandler = taskCompletionHandler;
    }

    public VirtualThreadsManagedFutureTask(AbstractManagedExecutorService executor, Callable<V> callable, Semaphore parallelTasksSemaphore, Runnable taskCompletionHandler) {
        super(executor, callable);
        this.parallelTasksSemaphore = parallelTasksSemaphore;
        this.taskCompletionHandler = taskCompletionHandler;
    }

    @Override
    public void run() {
        runPooled(() -> {
            super.run();
            return null;
        });
    }

    @Override
    public boolean runAndReset() {
        return runPooled(super::runAndReset);
    }

    private <T> T runPooled(Supplier<T> wrappedCallable) {
        try {
            acquireIfNotNull(parallelTasksSemaphore);
            return runAndTriggerEvents(wrappedCallable);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        } finally {
            releaseIfNotNull(parallelTasksSemaphore);
            runIfNotNull(taskCompletionHandler);
        }

    }

    private void runIfNotNull(Runnable handler) {
        if (handler != null) {
            handler.run();
        }
    }

    private void releaseIfNotNull(Semaphore semaphore) {
        if (semaphore != null) {
            semaphore.release();
        }
    }

    private void acquireIfNotNull(Semaphore semaphore) throws InterruptedException {
        if (semaphore != null) {
            semaphore.acquire();
        }
    }

    private <T> T runAndTriggerEvents(Supplier<T> wrappedCallable) {
        this.beforeExecute(Thread.currentThread());
        Exception taskException = null;
        try {
            return wrappedCallable.get();
        } catch (Exception e) {
            taskException = e;
            throw e;
        } finally {
            afterExecute(taskException);
        }
    }

    protected void beforeExecute(Thread t) {
        this.setupContext();
        this.starting(t);
    }

    protected void afterExecute(Throwable t) {
        this.done(t);
    }

}
