/*
 * Copyright (c) 2023, 2025 Contributors to the Eclipse Foundation.
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2024 Payara Foundation and/or its affiliates.
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

import jakarta.enterprise.concurrent.ManagedThreadFactory;

import java.lang.System.Logger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.glassfish.concurro.internal.ManagedFutureTask;
import org.glassfish.concurro.internal.ThreadExpiredException;
import org.glassfish.concurro.spi.ContextHandle;
import org.glassfish.concurro.spi.ContextSetupProvider;

import static java.lang.System.Logger.Level.WARNING;

/**
 * Implementation of ManagedThreadFactory interface.
 */
public class ManagedThreadFactoryImpl implements ManagedThreadFactory {

    private List<Thread> threads;
    private boolean stopped = false;
    private Lock lock; // protects threads and stopped

    private String name;
    final private ContextSetupProvider contextSetupProvider;
    // A non-null ContextService should be provided if thread context should
    // be setup before running the Runnable passed in through the newThread
    // method.
    // Context service could be null if the ManagedThreadFactoryImpl is
    // used for creating threads for ManagedExecutorService, where it is
    // not necessary to set up thread context at thread creation time. In that
    // case, thread context is set up before running each task.
    final private ContextServiceImpl contextService;
    // If there is a need to save context earlier than during newThread() (e.g. jndi lookup ManagedThreadFactory),
    // it is kept in savedContextHandleForSetup.
    protected ContextHandle savedContextHandleForSetup;
    private int priority;
    private long hungTaskThreshold = 0L; // in milliseconds
    private AtomicInteger threadIdSequence = new AtomicInteger();

    public static final String MANAGED_THREAD_FACTORY_STOPPED = "ManagedThreadFactory is stopped";

    public ManagedThreadFactoryImpl(String name) {
        this(name, null, Thread.NORM_PRIORITY);
    }

    public ManagedThreadFactoryImpl(String name, ContextServiceImpl contextService) {
        this(name, contextService, Thread.NORM_PRIORITY);
    }

    public ManagedThreadFactoryImpl(String name,
                                    ContextServiceImpl contextService,
                                    int priority) {
        this.name = name;
        this.contextService = contextService;
        this.contextSetupProvider = contextService != null? contextService.getContextSetupProvider(): null;
        this.priority = priority;
        threads = new ArrayList<>();
        lock = new ReentrantLock();
    }

    public String getName() {
        return name;
    }

    public long getHungTaskThreshold() {
        return hungTaskThreshold;
    }

    public void setHungTaskThreshold(long hungTaskThreshold) {
        this.hungTaskThreshold = hungTaskThreshold;
    }

    @Override
    public Thread newThread(Runnable r) {
        lock.lock();
        try {
            if (stopped) {
                // Do not create new thread and throw IllegalStateException if stopped
                throw new IllegalStateException(MANAGED_THREAD_FACTORY_STOPPED);
            }
            final ContextHandle contextHandleForSetup;
            if (savedContextHandleForSetup != null) {
                contextHandleForSetup = savedContextHandleForSetup;
            } else if (contextSetupProvider != null) {
                contextHandleForSetup = contextSetupProvider.saveContext(contextService);
            } else {
                contextHandleForSetup = null;
            }
            Thread newThread = createThread(r, contextHandleForSetup);
            newThread.setDaemon(true);
            threads.add(newThread);
            return newThread;
        }
        finally {
            lock.unlock();
        }
    }

    protected Thread createThread(final Runnable runnable, final ContextHandle contextHandleForSetup) {
        return createThreadInternal(runnable, contextHandleForSetup);
    }

    private Thread createThreadInternal(final Runnable r, final ContextHandle contextHandleForSetup) {
        ManagedThread newThread = new ManagedThread(r, contextHandleForSetup);
        newThread.setPriority(priority);
        return newThread;
    }

    protected ForkJoinWorkerThread createWorkerThread(final ForkJoinPool forkJoinPool, final ContextHandle contextHandleForSetup) {
        return createWorkerThreadInternal(forkJoinPool, contextHandleForSetup);
    }

    private WorkerThread createWorkerThreadInternal(final ForkJoinPool forkJoinPool, final ContextHandle contextHandleForSetup) {
        WorkerThread newThread = new WorkerThread(forkJoinPool, contextHandleForSetup);
        newThread.setPriority(priority);
        return newThread;
    }

    protected void removeThread(Thread t) {
        lock.lock();
        try {
            threads.remove(t);
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * @return an unmodifiable collection of threads in this ManagedThreadFactory.
     */
    protected Collection<Thread> getThreads() {
        lock.lock();
        try {
            return List.copyOf(threads);
        } finally {
            lock.unlock();
        }
    }
    public void taskStarting(Thread t, ManagedFutureTask task) {
        if (t instanceof ThreadWithTiming mt) {
            // called in thread t, so no need to worry about synchronization
            mt.notifyTaskStarting(task);
        }
    }

    public void taskDone(Thread t) {
        if (t instanceof ThreadWithTiming mt) {
            // called in thread t, so no need to worry about synchronization
            mt.notifyTaskDone();
        }
    }


    /**
     * Stop the ManagedThreadFactory instance. This should be used by the
     * component that creates the ManagedThreadFactory when the component is
     * stopped. All threads that this ManagedThreadFactory has created using
     * the #newThread() method are interrupted.
     */
    public void stop() {
        lock.lock();
        try {
            stopped = true;
            // interrupt all the threads created by this factory
            Iterator<Thread> iter = threads.iterator();
            while (iter.hasNext()) {
                Thread t = iter.next();
                shutdown(t);
                t.interrupt();
            }
        } finally {
            lock.unlock();
        }
    }


    /**
     * Make contextSetupProvider available for extending classes if necessary to implement context saving in other
     * moments than this implemention expects.
     *
     * @return contextSetupProvider
     */
    protected ContextSetupProvider getContextSetupProvider() {
        return contextSetupProvider;
    }

    @Override
    public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
        lock.lock();
        try {
            if(stopped) {
                // Do not create new ForkJoinWorkerThread and throw IllegalStateException if stopped
                throw new IllegalStateException(MANAGED_THREAD_FACTORY_STOPPED);
            }
            ContextHandle contextHandleForSetup = null;
            if (savedContextHandleForSetup != null) {
                contextHandleForSetup = savedContextHandleForSetup;
            } else if (contextSetupProvider != null) {
                contextHandleForSetup = contextSetupProvider.saveContext(contextService);
            }
            ForkJoinWorkerThread newThread = createWorkerThread(pool, contextHandleForSetup);
            threads.add(newThread);
            return newThread;
        } finally {
            lock.unlock();
        }
    }

    protected void shutdown(Thread t) {
        if (t instanceof AbstractManagedThread mt) {
            mt.shutdown(); // mark threads as shutting down
        }
    }

    /**
     * Thread aware of starting time and knowing, if it is hung.
     */
    public interface ThreadWithTiming {

        public void notifyTaskDone();

        public void notifyTaskStarting(ManagedFutureTask task);

        boolean isTaskHung(long now);
    }

    class WorkerThread extends ForkJoinWorkerThread implements ThreadWithTiming {
        private static final Logger LOG = System.getLogger(WorkerThread.class.getName());

        public static final long NOT_STARTED = -1;

        volatile long taskStartTime = NOT_STARTED;
        final ContextHandle contextHandleForSetup;

        public WorkerThread(ForkJoinPool pool, ContextHandle contextHandleForSetup) {
            super(pool);
            this.contextHandleForSetup = contextHandleForSetup;
            setUncaughtExceptionHandler(
                (t, e) -> LOG.log(WARNING, () -> "Thread with id " + t.getId() + " failed with exception: " + e, e));
        }

        @Override
        protected void onTermination(Throwable exception) {
            super.onTermination(exception);
        }

        @Override
        public void run() {
            try {
                if (contextHandleForSetup != null) {
                    contextSetupProvider.setup(contextHandleForSetup);
                }
                super.run();
            } catch (ThreadExpiredException ex) {
                LOG.log(WARNING, () -> "Thread with id " + getId() + " expired.", ex);
            }
        }

        @Override
        public boolean isTaskHung(long now) {
            return taskStartTime != NOT_STARTED && now - taskStartTime > hungTaskThreshold;
        }

        @Override
        public void notifyTaskDone() {
            taskStartTime = NOT_STARTED;
        }

        @Override
        public void notifyTaskStarting(ManagedFutureTask task) {
            taskStartTime = System.currentTimeMillis();
        }
    }

    /**
     * ManageableThread to be returned by {@code ManagedThreadFactory.newThread()}
     */
    public class ManagedThread extends AbstractManagedThread implements ThreadWithTiming {
        private static final Logger LOG = System.getLogger(ManagedThreadFactoryImpl.ManagedThread.class.getName());

        final ContextHandle contextHandleForSetup;
        volatile ManagedFutureTask task = null;
        volatile long taskStartTime = 0L;

        public ManagedThread(Runnable target, ContextHandle contextHandleForSetup) {
            super(target);
            setName(name + "-Thread-" + threadIdSequence.incrementAndGet());
            this.contextHandleForSetup = contextHandleForSetup;
            setUncaughtExceptionHandler(
                (t, e) -> LOG.log(WARNING, () -> "Thread with id " + t.getId() + " failed with exception: " + e, e));
        }

        @Override
        public void run() {
            ContextHandle handle = null;
            try {
                if (contextHandleForSetup != null) {
                    handle = contextSetupProvider.setup(contextHandleForSetup);
                }
                if (shutdown) {
                    // start thread in interrupted state if already marked for shutdown
                    this.interrupt();
                }
                super.run();
            } catch (ThreadExpiredException ex) {
                LOG.log(WARNING, () -> "Thread with id " + getId() + " expired.", ex);
            } finally {
                if (handle != null) {
                    contextSetupProvider.reset(handle);
                }
                removeThread(this);
            }
        }

        @Override
        boolean cancelTask() {
            var task = this.task;
            if (task != null) {
                return task.cancel(true);
            }
            return false;
        }

        @Override
        public String getTaskIdentityName() {
            var task = this.task;
            if (task != null) {
                return task.getTaskIdentityName();
            }
            return "null";
        }

        @Override
        public long getTaskRunTime(long now) {
            if (task != null && taskStartTime > 0) {
                long taskRunTime = now - taskStartTime;
                return taskRunTime > 0 ? taskRunTime : 0;
            }
            return 0;
        }

        @Override
        public long getThreadStartTime() {
            return threadStartTime;
        }

        @Override
        public boolean isTaskHung(long now) {
            if (hungTaskThreshold > 0) {
                return getTaskRunTime(now) - hungTaskThreshold > 0;
            }
            return false;
        }

        @Override
        public void notifyTaskDone() {
            taskStartTime = 0L;
            task = null;
        }

        @Override
        public void notifyTaskStarting(ManagedFutureTask task) {
            taskStartTime = System.currentTimeMillis();
            this.task = task;
        }
    }
}
