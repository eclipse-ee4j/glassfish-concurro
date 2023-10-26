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

import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.concurrent.ManagedExecutors;
import jakarta.enterprise.concurrent.ManagedTask;
import org.glassfish.enterprise.concurrent.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import jakarta.enterprise.concurrent.ManagedTaskListener;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.glassfish.enterprise.concurrent.internal.ManagedFutureTask;

/**
 * Implementation of ManagedExecutorService interface using Virtual Threads. See {@code AbstractManagedExecutorService}.
 */
public class VirtualThreadsManagedExecutorService extends AbstractManagedExecutorService implements ManagedTaskListener {

    protected final ExecutorService executor;

    // The adapter to be returned to the caller needs to have all the lifecycle
    // methods disabled
    protected final ManagedExecutorServiceAdapter adapter;
    protected VirtualThreadsManagedThreadFactory managedThreadFactory;

    private AtomicLong taskCount = new AtomicLong();

    private AtomicLong tasksCompleted = new AtomicLong();

    public VirtualThreadsManagedExecutorService(String name,
            VirtualThreadsManagedThreadFactory managedThreadFactory,
            long hungTaskThreshold,
            boolean longRunningTasks,
            int maxParallelTasks,
            ContextServiceImpl contextService,
            RejectPolicy rejectPolicy,
            BlockingQueue<Runnable> queue) {
        super(name, longRunningTasks,
                contextService,
                contextService != null ? contextService.getContextSetupProvider() : null,
                rejectPolicy);

        this.managedThreadFactory = managedThreadFactory != null ? managedThreadFactory : createDefaultManagedThreadFactory(name);
        this.managedThreadFactory.setHungTaskThreshold(hungTaskThreshold);

        // TODO - use the maxParallelTasks and queue to queue tasks if maxParallelTasks number of tasks is running
        if (maxParallelTasks <= 0) {
            throw new IllegalArgumentException("maxParallelTasks must be greater than 0, was " + maxParallelTasks);
        }
        executor = Executors.newThreadPerTaskExecutor(getManagedThreadFactory());
        adapter = new ManagedExecutorServiceAdapter(this);
    }

    public VirtualThreadsManagedExecutorService(String name,
            VirtualThreadsManagedThreadFactory managedThreadFactory,
            long hungTaskThreshold,
            boolean longRunningTasks,
            int maxParallelTasks,
            int queueCapacity,
            ContextServiceImpl contextService,
            RejectPolicy rejectPolicy) {
        this(name, managedThreadFactory, hungTaskThreshold, longRunningTasks, maxParallelTasks, contextService, rejectPolicy, createQueue(queueCapacity));
    }

    // Create a queue based on the value of queueCapacity.
    // If queueCapacity is 0, direct handoff queuing strategy will be used and a
    // SynchronousQueue will be created.
    // If queueCapacity is Integer.MAX_VALUE, an unbounded queue will be used.
    // For any other valid value for queueCapacity, a bounded queue will be created.
    private static BlockingQueue<Runnable> createQueue(int queueCapacity) throws IllegalArgumentException {
        BlockingQueue<Runnable> queue;
        if (queueCapacity == Integer.MAX_VALUE) {
            queue = new LinkedBlockingQueue<>();
        } else if (queueCapacity == 0) {
            queue = new SynchronousQueue<>();
        } else {
            queue = new ArrayBlockingQueue<>(queueCapacity);
        }
        return queue;
    }

    private VirtualThreadsManagedThreadFactory createDefaultManagedThreadFactory(String name) {
        VirtualThreadsManagedThreadFactory newManagedThreadFactory = new VirtualThreadsManagedThreadFactory(name + "-ManagedThreadFactory",
                null);
        managedThreadFactory = newManagedThreadFactory;
        return newManagedThreadFactory;
    }

    @Override
    public void execute(Runnable command) {
        ManagedFutureTask<Void> task = getNewTaskFor(command, null);
        executeManagedFutureTask(task);
    }

    // FIXME: put to top of the class
    final Set<ManagedFutureTask<?>> runningFutures = Collections.synchronizedSet(new HashSet<>());

    protected void executeManagedFutureTask(ManagedFutureTask<?> task) {
        task.submitted();
        getThreadPoolExecutor().execute(task);
        runningFutures.add(task);
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> result = super.shutdownNow();
        cancelRunningFutures();
        return result;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        cancelRunningFutures();
    }

    protected void cancelRunningFutures() {
        Set<ManagedFutureTask<?>> runningFuturesCopy;
        synchronized (runningFutures) {
            runningFuturesCopy = new HashSet<>(runningFutures);
        }
        runningFuturesCopy.stream().forEach(future -> {
            future.cancel(false);
        });
    }

    /**
     * Returns an adapter for ManagedExecutorService instance which has its life cycle operations disabled.
     *
     * @return The ManagedExecutorService instance with life cycle operations disabled for use by application
     * components.
     *
     */
    public ManagedExecutorServiceAdapter getAdapter() {
        return adapter;
    }

    @Override
    protected ExecutorService getThreadPoolExecutor() {
        return executor;
    }

    @Override
    public ManagedExecutorService getExecutorForTaskListener() {
        return adapter;
    }

    @Override
    protected <V> ManagedFutureTask<V> getNewTaskFor(Runnable r, V result) {
        Runnable task = r;
        ManagedTaskListener originalListener = null;
        Map<String, String> originalExecutionProperties = null;
        if (task instanceof ManagedTask) {
            ManagedTask managedTask = (ManagedTask) task;
            originalListener = managedTask.getManagedTaskListener();
            originalExecutionProperties = managedTask.getExecutionProperties();
        }
        task = ManagedExecutors.managedTask(task, originalExecutionProperties,
                new MultiManagedTaskListener(this, originalListener));
        return new EventTriggeringManagedFutureTask<>(this, task, result);
    }

    @Override
    protected ManagedFutureTask getNewTaskFor(Callable callable) {
        return new ManagedFutureTask(this, callable);
    }

    @Override
    public long getTaskCount() {
        return taskCount.get();
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

    protected boolean isTaskHung(Thread thread, long now) {
        return managedThreadFactory.isTaskHung(thread, now);
    }

    @Override
    public ManagedThreadFactoryImpl getManagedThreadFactory() {
        return managedThreadFactory;
    }
}
