/*
 * Copyright (c) 2023, 2024 Contributors to the Eclipse Foundation.
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
package org.glassfish.concurro.virtualthreads;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.glassfish.concurro.AbstractManagedExecutorService;
import org.glassfish.concurro.internal.ManagedFutureTask;

/**
 * A ManagedFutureTask for virtual threads executors. Automatically triggeres events before and after executing the
 * task. Implements pooling via provided semaphores.
 */
public class VirtualThreadsManagedFutureTask<V> extends ManagedFutureTask<V> {

    private Semaphore parallelTasksSemaphore = null;

    private Runnable taskCompletionHandler = null;

    private Consumer<VirtualThreadsManagedFutureTask<V>> taskStartedHandler = null;

    public VirtualThreadsManagedFutureTask(AbstractManagedExecutorService executor, Runnable runnable, V result, Semaphore parallelTasksSemaphore) {
        super(executor, runnable, result);
        this.parallelTasksSemaphore = parallelTasksSemaphore;
    }

    public VirtualThreadsManagedFutureTask(AbstractManagedExecutorService executor, Callable<V> callable, Semaphore parallelTasksSemaphore) {
        super(executor, callable);
        this.parallelTasksSemaphore = parallelTasksSemaphore;
    }

    public void setTaskCompletionHandler(Runnable taskCompletionHandler) {
        this.taskCompletionHandler = taskCompletionHandler;
    }

    public void setTaskStartedHandler(Consumer<VirtualThreadsManagedFutureTask<V>> taskStartedHandler) {
        this.taskStartedHandler = taskStartedHandler;
    }

    @Override
    public void run() {
        runPooled(() -> {
            super.run();
            return null;
        }, null);
    }

    @Override
    public boolean runAndReset() {
        return runPooled(super::runAndReset, false);
    }

    private <T> T runPooled(Supplier<T> wrappedCallable, T defaultResult) {
        try {
            try {
                acquireIfNotNull(parallelTasksSemaphore);
            } catch (InterruptedException ex) {
                /*
              Executor.shutdownNow() called, task interrupted before started.
              Ignore and only report as not started from the shutdownNow() method
                 */
                return defaultResult;
            }
            triggerIfNotNull(taskStartedHandler, this);
            return runAndTriggerEvents(wrappedCallable);
        } finally {
            releaseIfNotNull(parallelTasksSemaphore);
            triggerIfNotNull(taskCompletionHandler);
        }

    }

    private void triggerIfNotNull(Runnable handler) {
        if (handler != null) {
            handler.run();
        }
    }

    private void triggerIfNotNull(Consumer<VirtualThreadsManagedFutureTask<V>> handler,
            VirtualThreadsManagedFutureTask<V> task) {
        if (handler != null) {
            handler.accept(task);
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
