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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import org.glassfish.enterprise.concurrent.internal.ManagedFutureTask;
import org.glassfish.enterprise.concurrent.internal.ManagedThreadPoolExecutor;

/**
 * Implementation of ManagedExecutorService interface. See {@code AbstractManagedExecutorService}.
 */
public class ManagedExecutorServiceImpl extends AbstractManagedExecutorService {
    
    protected final ManagedThreadPoolExecutor threadPoolExecutor;

    // The adapter to be returned to the caller needs to have all the lifecycle 
    // methods disabled
    protected final ManagedExecutorServiceAdapter adapter;

    public ManagedExecutorServiceImpl(String name,
            ManagedThreadFactoryImpl managedThreadFactory,
            long hungTaskThreshold,
            boolean longRunningTasks,
            int corePoolSize, int maxPoolSize, long keepAliveTime, 
            TimeUnit keepAliveTimeUnit,
            long threadLifeTime,
            ContextServiceImpl contextService,
            RejectPolicy rejectPolicy,
            BlockingQueue<Runnable> queue) {
        super(name, managedThreadFactory, hungTaskThreshold, longRunningTasks,
                contextService,
                contextService != null? contextService.getContextSetupProvider(): null,
                rejectPolicy);
        threadPoolExecutor = new ManagedThreadPoolExecutor(corePoolSize, maxPoolSize, 
                keepAliveTime, keepAliveTimeUnit, queue, 
                this.managedThreadFactory);
        threadPoolExecutor.setThreadLifeTime(threadLifeTime);
        adapter = new ManagedExecutorServiceAdapter(this);
    }
    
    public ManagedExecutorServiceImpl(String name,
            ManagedThreadFactoryImpl managedThreadFactory,
            long hungTaskThreshold,
            boolean longRunningTasks,
            int corePoolSize, int maxPoolSize, long keepAliveTime, 
            TimeUnit keepAliveTimeUnit, 
            long threadLifeTime,
            int queueCapacity,
            ContextServiceImpl contextService,
            RejectPolicy rejectPolicy) {
        super(name, managedThreadFactory, hungTaskThreshold, longRunningTasks,
                contextService,
                contextService != null? contextService.getContextSetupProvider(): null,
                rejectPolicy);

        // Create a queue for ManagedThreadPoolExecutor based on the values
        // of corePoolSize and queueCapacity.
        // If queueCapacity is 0, or
        // queueCapacity is Integer.MAX_VALUE and corePoolSize is 0,
        // direct handoff queuing strategy will be used and a
        // SynchronousQueue will be created.
        // If queueCapacity is Integer.MAX_VALUE but corePoolSize is not 0,
        // an unbounded queue will be used.
        // For any other valid value for queueCapacity, a bounded queue
        // wil be created.
        if (queueCapacity < 0) {
            throw new IllegalArgumentException();
        }
        BlockingQueue<Runnable> queue;
        if (queueCapacity == Integer.MAX_VALUE) {
            if (corePoolSize == 0) {
                queue = new SynchronousQueue<>();
            }
            else {
                queue = new LinkedBlockingQueue<>();
            }
        } else if (queueCapacity == 0) {
            queue = new SynchronousQueue<>();
        } else {
            queue = new ArrayBlockingQueue<>(queueCapacity); 
        }

        threadPoolExecutor = new ManagedThreadPoolExecutor(corePoolSize, maxPoolSize, 
                keepAliveTime, keepAliveTimeUnit, queue, 
                this.managedThreadFactory);
        threadPoolExecutor.setThreadLifeTime(threadLifeTime);
        adapter = new ManagedExecutorServiceAdapter(this);
    }
 
    @Override
    public void execute(Runnable command) {
        ManagedFutureTask<Void> task = getNewTaskFor(command, null);
        task.submitted();
        threadPoolExecutor.execute(task);
    }

    /**
     * Returns an adapter for ManagedExecutorService instance which has its
     * life cycle operations disabled.
     * 
     * @return The ManagedExecutorService instance with life cycle operations
     *         disabled for use by application components.
     **/
    public ManagedExecutorServiceAdapter getAdapter() {
        return adapter;
    }

    @Override
    protected ExecutorService getThreadPoolExecutor() {
        return threadPoolExecutor;
    }

    @Override
    public ManagedExecutorService getExecutorForTaskListener() {
        return adapter;
    }

    @Override
    protected <V> ManagedFutureTask<V> getNewTaskFor(Runnable r, V result) {
        return new ManagedFutureTask<>(this, r, result);
    }
    
    @Override
    protected ManagedFutureTask getNewTaskFor(Callable callable) {
        return new ManagedFutureTask(this, callable);
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
