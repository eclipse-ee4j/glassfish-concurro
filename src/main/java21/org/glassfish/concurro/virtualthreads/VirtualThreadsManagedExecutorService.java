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

import org.glassfish.concurro.internal.MultiManagedTaskListener;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.concurrent.ManagedExecutors;
import jakarta.enterprise.concurrent.ManagedTask;
import org.glassfish.concurro.*;
import java.util.concurrent.ExecutorService;
import jakarta.enterprise.concurrent.ManagedTaskListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import org.glassfish.concurro.internal.ManagedFutureTask;

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

    private Semaphore parallelTasksSemaphore = null;
    int maxParallelTasks;

    private Semaphore queuedTasksSemaphore = null;
    int queueCapacity;

    final private Set<ManagedFutureTask<?>> runningFutures = ConcurrentHashMap.newKeySet();
    final private Set<ManagedFutureTask<?>> tasksInQueue = ConcurrentHashMap.newKeySet();


    public VirtualThreadsManagedExecutorService(String name,
            VirtualThreadsManagedThreadFactory managedThreadFactory,
            long hungTaskThreshold,
            boolean longRunningTasks,
            int maxParallelTasks,
            int queueCapacity,
            ContextServiceImpl contextService,
            RejectPolicy rejectPolicy) {
        super(name, longRunningTasks,
                contextService,
                contextService != null ? contextService.getContextSetupProvider() : null,
                rejectPolicy);

        this.managedThreadFactory = managedThreadFactory != null ? managedThreadFactory : createDefaultManagedThreadFactory(name);
        this.managedThreadFactory.setHungTaskThreshold(hungTaskThreshold);

        this.maxParallelTasks = maxParallelTasks;
        this.queueCapacity = queueCapacity;
        if (maxParallelTasks > 0) {
            parallelTasksSemaphore = new Semaphore(maxParallelTasks, true);
            if (queueCapacity > 0) {
                int virtualCapacity = queueCapacity + maxParallelTasks;
                if (virtualCapacity <= 0) {
                    // int overflow; queue capacity is often MAX_VALUE
                    virtualCapacity = Integer.MAX_VALUE;
                }
                queuedTasksSemaphore = new Semaphore(virtualCapacity, true);
            }
        }
        executor = Executors.newThreadPerTaskExecutor(getManagedThreadFactory());
        adapter = new ManagedExecutorServiceAdapter(this);
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

    @Override
    protected void executeManagedFutureTask(ManagedFutureTask<?> task) {
        if (queuedTasksSemaphore == null || queuedTasksSemaphore.tryAcquire()) {
            task.submitted();
            tasksInQueue.add(task);
            getThreadPoolExecutor().execute(task);
            runningFutures.add(task);
        } else {
            throw new RejectedExecutionException("Too many tasks submitted (available = " + (queuedTasksSemaphore == null ? "UNUSED" : queuedTasksSemaphore.availablePermits()) + ", maxParallelTasks = " + maxParallelTasks + ", queueCapacity = " + queueCapacity);
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        super.shutdownNow();
        cancelRunningFutures();
        return new ArrayList<>(tasksInQueue);
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
    public AbstractManagedExecutorServiceAdapter getAdapter() {
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
        Runnable task = turnToTaskWithListener(r, result, this);
        VirtualThreadsManagedFutureTask<V> managedTask = new VirtualThreadsManagedFutureTask<>(this, task, result, parallelTasksSemaphore);
        addTaskListeners(managedTask);
        return managedTask;
    }

    private <V> void addTaskListeners(VirtualThreadsManagedFutureTask<V> managedTask) {
        managedTask.setTaskCompletionHandler(() -> {
            if (queuedTasksSemaphore != null) {
                queuedTasksSemaphore.release();
            }
        });
        managedTask.setTaskStartedHandler(startedTask -> {
            tasksInQueue.remove(startedTask);
        });
    }

    private <V> Runnable turnToTaskWithListener(Runnable r, V result, ManagedTaskListener listener) throws IllegalArgumentException {
        Runnable task = r;
        ManagedTaskListener originalListener = null;
        Map<String, String> originalExecutionProperties = null;
        if (task instanceof ManagedTask managedTask) {
            originalListener = managedTask.getManagedTaskListener();
            originalExecutionProperties = managedTask.getExecutionProperties();
        }
        return ManagedExecutors.managedTask(task, originalExecutionProperties,
                new MultiManagedTaskListener(listener, originalListener));
    }

    @Override
    protected <V> ManagedFutureTask<V> getNewTaskFor(Callable<V> callable) {
        Callable task = turnToTaskWithListener(callable, this);
        VirtualThreadsManagedFutureTask managedTask = new VirtualThreadsManagedFutureTask<>(this, task, parallelTasksSemaphore);
        addTaskListeners(managedTask);
        return managedTask;
    }

    private <V> Callable turnToTaskWithListener(Callable c, ManagedTaskListener listener) throws IllegalArgumentException {
        Callable task = c;
        ManagedTaskListener originalListener = null;
        Map<String, String> originalExecutionProperties = null;
        if (task instanceof ManagedTask managedTask) {
            originalListener = managedTask.getManagedTaskListener();
            originalExecutionProperties = managedTask.getExecutionProperties();
        }
        return ManagedExecutors.managedTask(task, originalExecutionProperties,
                new MultiManagedTaskListener(listener, originalListener));
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
