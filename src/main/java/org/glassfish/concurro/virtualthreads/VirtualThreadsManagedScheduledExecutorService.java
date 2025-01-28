/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
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

import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.concurrent.Trigger;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.glassfish.concurro.ContextServiceImpl;
import org.glassfish.concurro.ManagedScheduledExecutorServiceAdapter;
import org.glassfish.concurro.internal.ManagedFutureTask;

/**
 * Dummy Implementation of ManagedScheduledExecutorService interface using Virtual
 * Threads.
 *
 * @author Kalin Chan
 */
public class VirtualThreadsManagedScheduledExecutorService extends VirtualThreadsManagedExecutorService
        implements ManagedScheduledExecutorService {

    public VirtualThreadsManagedScheduledExecutorService(String name,
            VirtualThreadsManagedThreadFactory managedThreadFactory,
            long hungTaskThreshold,
            boolean longRunningTasks,
            int maxParallelTasks,
            int queueCapacity,
            ContextServiceImpl contextService,
            RejectPolicy rejectPolicy) {
        super(null, null, 0, false,
                0, 0, null, null);
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, Trigger trigger) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, Trigger trigger) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public void execute(Runnable command) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public Future<?> submit(Runnable task) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    protected <V> ManagedFutureTask<V> getNewTaskFor(Runnable r, V result) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    protected <V> ManagedFutureTask<V> getNewTaskFor(Callable<V> callable) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    protected void executeManagedFutureTask(ManagedFutureTask<?> task) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public ManagedScheduledExecutorServiceAdapter getAdapter() {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public ManagedExecutorService getExecutorForTaskListener() {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    protected ExecutorService getThreadPoolExecutor() {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }
}
