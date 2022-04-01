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
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.concurrent.Trigger;
import org.glassfish.enterprise.concurrent.internal.ManagedFutureTask;
import org.glassfish.enterprise.concurrent.internal.ManagedScheduledThreadPoolExecutor;

/**
 * Implementation of ManagedScheduledExecutorService interface
 */
public class ManagedScheduledExecutorServiceImpl extends AbstractManagedExecutorService 
    implements ManagedScheduledExecutorService {

    protected ManagedScheduledThreadPoolExecutor threadPoolExecutor;
    protected final ManagedScheduledExecutorServiceAdapter adapter;
    

    public ManagedScheduledExecutorServiceImpl(String name, 
            ManagedThreadFactoryImpl managedThreadFactory, 
            long hungTaskThreshold, 
            boolean longRunningTasks, 
            int corePoolSize, 
            long keepAliveTime, 
            TimeUnit keepAliveTimeUnit,
            long threadLifeTime,
            ContextServiceImpl contextService,
            RejectPolicy rejectPolicy) {
        super(name, managedThreadFactory, hungTaskThreshold, longRunningTasks,
                contextService,
                contextService != null? contextService.getContextSetupProvider(): null,
                rejectPolicy);

        threadPoolExecutor = new ManagedScheduledThreadPoolExecutor(corePoolSize, 
                this.managedThreadFactory);
        threadPoolExecutor.setKeepAliveTime(keepAliveTime, keepAliveTimeUnit);
        threadPoolExecutor.setThreadLifeTime(threadLifeTime);
        adapter = new ManagedScheduledExecutorServiceAdapter(this);
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
    protected ExecutorService getThreadPoolExecutor() {
        return threadPoolExecutor;
    }

   /**
     * Returns an adapter for the ManagedScheduledExceutorService instance which
     * has its life cycle operations disabled.
     * 
     * @return The ManagedScheduledExecutorService instance with life cycle 
     *         operations disabled for use by application components.
     */
    public ManagedScheduledExecutorServiceAdapter getAdapter() {
        return adapter  ;
    }

    @Override
    public ManagedExecutorService getExecutorForTaskListener() {
        return adapter;
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
    protected void executeManagedFutureTask(ManagedFutureTask task) {
        // task.submitted() will be called from threadPoolExecutor.delayExecute()
        threadPoolExecutor.executeManagedTask(task);
    }

    @Override
    public long getTaskCount() {
        return threadPoolExecutor.getTaskCount();
    }
    
    @Override
    public long getCompletedTaskCount() {
        return threadPoolExecutor.getCompletedTaskCount();
    }

}
