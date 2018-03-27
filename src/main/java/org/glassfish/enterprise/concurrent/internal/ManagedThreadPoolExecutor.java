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

package org.glassfish.enterprise.concurrent.internal;

import java.util.concurrent.*;
import org.glassfish.enterprise.concurrent.AbstractManagedThread;

/**
 * ThreadPoolExecutor for running tasks submitted to ManagedExecutorServiceImpl.
 */
public class ManagedThreadPoolExecutor extends ThreadPoolExecutor {

    private long threadLifeTime = 0L; // in seconds
    
    public ManagedThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    public ManagedThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public ManagedThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public ManagedThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public void setThreadLifeTime(long threadLifeTime) {
        this.threadLifeTime = threadLifeTime;
        if (threadLifeTime > 0) {
            long keepAliveTime = getKeepAliveTime(TimeUnit.SECONDS);
            if (keepAliveTime == 0 || threadLifeTime < keepAliveTime) {
                setKeepAliveTime(threadLifeTime, TimeUnit.SECONDS);
                allowCoreThreadTimeOut(true);
            }
        }
    }

    
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);

        ManagedFutureTask task = (ManagedFutureTask) r;
        try {
            task.done(t);
        }
        finally {
            task.resetContext();
            // Kill thread if thread older than threadLifeTime
            if (threadLifeTime > 0) {
                Thread thread = Thread.currentThread();
                if (thread instanceof AbstractManagedThread) {
                    long threadStartTime = ((AbstractManagedThread)thread).getThreadStartTime();
                    if ((System.currentTimeMillis() - threadStartTime)/1000 > threadLifeTime) {
                        throw new ThreadExpiredException();
                    }
                }
            }
        }
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        
        ManagedFutureTask task = (ManagedFutureTask) r;
        task.setupContext();
        task.starting(t);
    }
    
}
