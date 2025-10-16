/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * Copyright (c) 2022, 2024 Payara Foundation and/or its affiliates.
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
package org.glassfish.concurro;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.concurrent.ManagedExecutors;
import jakarta.enterprise.concurrent.ManagedTask;
import jakarta.enterprise.concurrent.ManagedTaskListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.glassfish.concurro.ManagedThreadFactoryImpl.WorkerThread;
import org.glassfish.concurro.internal.ManagedFutureTask;
import org.glassfish.concurro.internal.MultiManagedTaskListener;

/**
 * Implementation of ManagedExecutorService interface. See
 * {@code AbstractManagedExecutorService}.
 *
 * @author Luise Neto, Petr Aubrecht
 */
public class ForkJoinManagedExecutorService extends AbstractPlatformThreadExecutorService implements ManagedTaskListener {

    // The adapter to be returned to the caller needs to have all the lifecycle
    // methods disabled
    protected final ManagedExecutorServiceAdapter adapter;

    protected final ForkJoinPool pool;

    private final Map<ManagedFutureTask<?>, Runnable> runningFutures = new ConcurrentHashMap<>();
    private final AtomicLong taskCount = new AtomicLong();
    private final AtomicLong tasksCompleted = new AtomicLong();

    public ForkJoinManagedExecutorService(String name,
        ManagedThreadFactoryImpl managedThreadFactory,
        long hungTaskThreshold,
        boolean longRunningTasks,
        int maxPoolSize,
        long keepAliveTime,
        TimeUnit keepAliveTimeUnit,
        int queueCapacity,
        ContextServiceImpl contextService,
        RejectPolicy rejectPolicy) {
        this(name, managedThreadFactory, hungTaskThreshold, longRunningTasks,
            maxPoolSize, keepAliveTime, keepAliveTimeUnit, contextService,
            rejectPolicy);
    }

    public ForkJoinManagedExecutorService(String name,
            ManagedThreadFactoryImpl managedThreadFactory,
            long hungTaskThreshold,
            boolean longRunningTasks,
            int maxPoolSize,
            long keepAliveTime,
            TimeUnit keepAliveTimeUnit,
            ContextServiceImpl contextService,
            RejectPolicy rejectPolicy) {
        super(name, managedThreadFactory, hungTaskThreshold, longRunningTasks,
                contextService,
                contextService != null ? contextService.getContextSetupProvider() : null,
                rejectPolicy);
        this.pool = new ForkJoinPool(maxPoolSize, this.managedThreadFactory, null, false, 0, maxPoolSize,
            1, null, keepAliveTime, TimeUnit.SECONDS);
        this.adapter = new ManagedExecutorServiceAdapter(this);
    }

    @Override
    public void execute(Runnable command) {
        ManagedFutureTask<Void> task = getNewTaskFor(command, null);
        task.submitted();
        runningFutures.put(task, command);
        pool.execute(task);
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
    public ManagedExecutorServiceAdapter getAdapter() {
        return adapter;
    }

    @Override
    public ManagedExecutorService getExecutorForTaskListener() {
        return adapter;
    }

    @Override
    protected <V> ManagedFutureTask<V> getNewTaskFor(Runnable r, V result) {
        StatefulRunnable statefulRunnable = new StatefulRunnable(r);
        Runnable notifiedRunnable = ManagedExecutors.managedTask(statefulRunnable,
                (r instanceof ManagedTask) ? ((ManagedTask) r).getExecutionProperties() : null,
                new MultiManagedTaskListener(this,
                        (r instanceof ManagedTask) ? ((ManagedTask) r).getManagedTaskListener() : null));
        ManagedFutureTask<V> managedFutureTask = new ManagedFutureTask<>(this, notifiedRunnable, result);
//        managedFutureTask.setTaskDoneCallback(future -> taskDone(future, adapter, this, null));
        statefulRunnable.setTask(managedFutureTask);
        runningFutures.put(managedFutureTask, notifiedRunnable);
        return managedFutureTask;
    }

    public class StatefulRunnable implements Runnable {

        private final Runnable runnable;
        private ManagedFutureTask<?> task;

        public StatefulRunnable(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void run() {
            Thread thread = Thread.currentThread();
            if (thread instanceof ManagedThreadFactoryImpl.WorkerThread workerThread) {
                workerThread.notifyTaskStarting(task);
            }
            task.starting(thread);
            runnable.run();
            taskDone(task, adapter, runnable, null);
            if (thread instanceof ManagedThreadFactoryImpl.WorkerThread workerThread) {
                workerThread.notifyTaskDone();
            }
        }

        public void setTask(ManagedFutureTask<?> task) {
            this.task = task;
        }
    }

    @Override
    protected <T> ManagedFutureTask<T> getNewTaskFor(Callable<T> callable) {
        return new ManagedFutureTask<>(this, callable);
    }

    @Override
    public List<Runnable> shutdownNow() {
        super.shutdownNow();
        ArrayList<Runnable> runnables = new ArrayList<>(runningFutures.values());
        ArrayList<ManagedFutureTask<?>> copyOfRunningFutures = new ArrayList<>(runningFutures.keySet());
        copyOfRunningFutures.stream().forEach(future -> future.cancel(false));
        return Collections.unmodifiableList(runnables);
    }

    @Override
    public long getTaskCount() {
        return taskCount.get();
    }

    @Override
    public ManagedThreadFactoryImpl getManagedThreadFactory() {
        return managedThreadFactory;
    }

    @Override
    protected ExecutorService getThreadPoolExecutor() {
        return pool;
    }

    @Override
    protected boolean isTaskHung(Thread thread, long now) {
        WorkerThread managedThread = (WorkerThread) thread;
        return managedThread.isTaskHung(now);
    }

    @Override
    public long getCompletedTaskCount() {
        return tasksCompleted.get();
    }

    @Override
    public void taskSubmitted(Future<?> future, ManagedExecutorService executor, Object task) {
        taskCount.incrementAndGet();
    }

    @Override
    public void taskAborted(Future<?> future, ManagedExecutorService executor, Object task, Throwable exception) {
        runningFutures.remove(future);
        taskCount.decrementAndGet();
    }

    @Override
    public void taskDone(Future<?> future, ManagedExecutorService executor, Object task, Throwable exception) {
        runningFutures.remove(future);
        tasksCompleted.incrementAndGet();
    }

    @Override
    public void taskStarting(Future<?> future, ManagedExecutorService executor, Object task) {
    }
}
