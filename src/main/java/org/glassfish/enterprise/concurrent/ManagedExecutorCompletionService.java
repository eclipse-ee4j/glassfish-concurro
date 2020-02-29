/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.enterprise.concurrent;

import java.util.concurrent.*;
import jakarta.enterprise.concurrent.ManagedTaskListener;
import org.glassfish.enterprise.concurrent.internal.ManagedFutureTask;
import org.glassfish.enterprise.concurrent.internal.TaskDoneCallback;

/**
 *
 * Adopted from java.util.concurrent.ExecutorCompletionService with support
 * for ManagedTaskListener.
 */
public class ManagedExecutorCompletionService<V> implements TaskDoneCallback {
    private final AbstractManagedExecutorService executor;
    private final BlockingQueue<Future<V>> completionQueue;

    /**
     * Creates an ExecutorCompletionService using the supplied
     * executor for base task execution and a
     * {@link LinkedBlockingQueue} as a completion queue.
     *
     * @param executor the executor to use
     * @throws NullPointerException if executor is {@code null}
     */
    public ManagedExecutorCompletionService(AbstractManagedExecutorService executor) {
        if (executor == null) {
            throw new NullPointerException();
        }
        this.executor = executor;
        this.completionQueue = new LinkedBlockingQueue<Future<V>>();
    }

    /**
     * Creates an ExecutorCompletionService using the supplied
     * executor for base task execution and the supplied queue as its
     * completion queue.
     *
     * @param executor the executor to use
     * @param completionQueue the queue to use as the completion queue
     *        normally one dedicated for use by this service. This
     *        queue is treated as unbounded -- failed attempted
     *        {@code Queue.add} operations for completed taskes cause
     *        them not to be retrievable.
     * @throws NullPointerException if executor or completionQueue are {@code null}
     */
    public ManagedExecutorCompletionService(AbstractManagedExecutorService executor,
                                     BlockingQueue<Future<V>> completionQueue) {
        if (executor == null || completionQueue == null) {
            throw new NullPointerException();
        }
        this.executor = executor;
        this.completionQueue = completionQueue;
    }
    
    public Future<V> submit(Callable<V> task) {
        if (task == null) {
            throw new NullPointerException();
        }
        ManagedFutureTask<V> f = executor.getNewTaskFor(task);
        f.setTaskDoneCallback(this);
        executor.executeManagedFutureTask(f);
        return f;
    }

    public Future<V> submit(Runnable task, V result, ManagedTaskListener taskListener) {
        if (task == null) {
            throw new NullPointerException();
        }
        ManagedFutureTask<V> f = executor.getNewTaskFor(task, result);
        f.setTaskDoneCallback(this);
        executor.executeManagedFutureTask(f);
        return f;
    }

    
    public Future<V> take() throws InterruptedException {
        return completionQueue.take();
    }

    public Future<V> poll() {
        return completionQueue.poll();
    }

    public Future<V> poll(long timeout, TimeUnit unit)
            throws InterruptedException {
        return completionQueue.poll(timeout, unit);
    }

    @Override
    public void taskDone(ManagedFutureTask future) {
        completionQueue.add(future); 
    }
}

