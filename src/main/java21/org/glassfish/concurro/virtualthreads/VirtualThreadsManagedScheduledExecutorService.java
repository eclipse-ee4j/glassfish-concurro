/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
import org.glassfish.concurro.internal.ManagedScheduledThreadPoolExecutor;

/**
 * Implementation of ManagedScheduledExecutorService interface using Virtual
 * Threads. See {@code AbstractPlatformThreadExecutorService}.
 *
 * @author aubi
 */
public class VirtualThreadsManagedScheduledExecutorService extends VirtualThreadsManagedExecutorService
        implements ManagedScheduledExecutorService {

    protected final ManagedScheduledExecutorServiceAdapter scheduledAdapter;
    protected final ManagedScheduledThreadPoolExecutor threadPoolExecutor;

    public VirtualThreadsManagedScheduledExecutorService(String name,
            VirtualThreadsManagedThreadFactory managedThreadFactory,
            long hungTaskThreshold,
            boolean longRunningTasks,
            int maxParallelTasks,
            int queueCapacity,
            ContextServiceImpl contextService,
            RejectPolicy rejectPolicy) {
        super(name, managedThreadFactory, hungTaskThreshold, longRunningTasks,
                maxParallelTasks, queueCapacity, contextService, rejectPolicy);
        scheduledAdapter = new ManagedScheduledExecutorServiceAdapter(this);

        threadPoolExecutor = new ManagedScheduledThreadPoolExecutor(0/*maxParallelTasks*/, // FIXME: should be 0?
                this.managedThreadFactory);
        threadPoolExecutor.setMaximumPoolSize(maxParallelTasks); // FIXME: MAXINT, limit/block by thread itself, look at VirtualThreadsManagedExecutorService.parallelTasksSemaphore
        threadPoolExecutor.setKeepAliveTime(0, TimeUnit.SECONDS);
        threadPoolExecutor.setThreadLifeTime(0);
    }

    private VirtualThreadsManagedThreadFactory createDefaultManagedThreadFactory(String name) {
        VirtualThreadsManagedThreadFactory newManagedThreadFactory = new VirtualThreadsManagedThreadFactory(name + "-ManagedThreadFactory",
                null);
        managedThreadFactory = newManagedThreadFactory;
        return newManagedThreadFactory;
    }
    @Override
    public ScheduledFuture<?> schedule(Runnable command, Trigger trigger) {
        return threadPoolExecutor.schedule(this, command, trigger);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, Trigger trigger) {
        return threadPoolExecutor.schedule(this, callable, trigger);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return threadPoolExecutor.schedule(this, command, null, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return threadPoolExecutor.schedule(this, callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return threadPoolExecutor.scheduleAtFixedRate(this, command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return threadPoolExecutor.scheduleWithFixedDelay(this, command, initialDelay, delay, unit);
    }

    @Override
    public void execute(Runnable command) {
        threadPoolExecutor.schedule(this, command, null, 0L, TimeUnit.NANOSECONDS);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return threadPoolExecutor.schedule(this, task, null, 0L, TimeUnit.NANOSECONDS);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return threadPoolExecutor.schedule(this, task, result, 0L, TimeUnit.NANOSECONDS);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return threadPoolExecutor.schedule(this, task, 0L, TimeUnit.NANOSECONDS);
    }

    @Override
    protected <V> ManagedFutureTask<V> getNewTaskFor(Runnable r, V result) {
        return threadPoolExecutor.newTaskFor(this, r, result);
    }

    @Override
    protected <V> ManagedFutureTask<V> getNewTaskFor(Callable<V> callable) {
        return threadPoolExecutor.newTaskFor(this, callable);
    }

    @Override
    protected void executeManagedFutureTask(ManagedFutureTask<?> task) {
        threadPoolExecutor.executeManagedTask(task);
    }

    /**
     * Returns an adapter for ManagedExecutorService instance which has its life
     * cycle operations disabled.
     *
     * @return The ManagedExecutorService instance with life cycle operations
     * disabled for use by application components.
     *
     */
    @Override
    public ManagedScheduledExecutorServiceAdapter getAdapter() {
        return scheduledAdapter;
    }

    @Override
    public ManagedExecutorService getExecutorForTaskListener() {
        return scheduledAdapter;
    }

    @Override
    protected ExecutorService getThreadPoolExecutor() {
        return threadPoolExecutor;
    }
}
