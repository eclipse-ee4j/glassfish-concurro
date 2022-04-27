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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.enterprise.concurrent.ManagedThreadFactory;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import org.glassfish.enterprise.concurrent.internal.ManagedFutureTask;
import org.glassfish.enterprise.concurrent.internal.ThreadExpiredException;
import org.glassfish.enterprise.concurrent.spi.ContextHandle;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;

/**
 * Implementation of ManagedThreadFactory interface.
 */
public class ManagedThreadFactoryImpl implements ManagedThreadFactory {

    private List<AbstractManagedThread> threads;
    private boolean stopped = false;
    private Lock lock; // protects threads and stopped

    private String name;
    final protected ContextSetupProvider contextSetupProvider;
    // A non-null ContextService should be provided if thread context should
    // be setup before running the Runnable passed in through the newThread
    // method.
    // Context service could be null if the ManagedThreadFactoryImpl is
    // used for creating threads for ManagedExecutorService, where it is
    // not necessary to set up thread context at thread creation time. In that
    // case, thread context is set up before running each task.
    final protected ContextServiceImpl contextService;
    // Java Concurrency requires saving context during jndi lookup ManagedThreadFactory,
    // it is kept in savedContextHandleForSetup.
    protected ContextHandle savedContextHandleForSetup = null;
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
            ContextHandle contextHandleForSetup = null;
            if (savedContextHandleForSetup != null) {
                contextHandleForSetup = savedContextHandleForSetup;
            } else if (contextSetupProvider != null) {
                contextHandleForSetup = contextSetupProvider.saveContext(contextService);
            }
            AbstractManagedThread newThread = createThread(r, contextHandleForSetup);
            newThread.setDaemon(true);
            threads.add(newThread);
            return newThread;
        }
        finally {
            lock.unlock();
        }
    }

    protected AbstractManagedThread createThread(final Runnable r, final ContextHandle contextHandleForSetup) {
        if (System.getSecurityManager() == null) {
            return createThreadInternal(r, contextHandleForSetup);
        } else {
            return (ManagedThread) AccessController.doPrivileged(new PrivilegedAction() {
                @Override
                public Object run() {
                    return createThreadInternal(r, contextHandleForSetup);
                }
              });
        }
    }

    private ManagedThread createThreadInternal(final Runnable r, final ContextHandle contextHandleForSetup) {
        ManagedThread newThread = new ManagedThread(r, contextHandleForSetup);
        newThread.setPriority(priority);
        return newThread;
    }

    protected ForkJoinWorkerThread createWorkerThread(final ForkJoinPool forkJoinPool, final ContextHandle contextHandleForSetup) {
        if (System.getSecurityManager() == null) {
            return createWorkerThreadInternal(forkJoinPool, contextHandleForSetup);
        } else {
            return (ForkJoinWorkerThread) AccessController.doPrivileged(new PrivilegedAction() {
                        @Override
                        public Object run() {
                            return createWorkerThreadInternal(forkJoinPool, contextHandleForSetup);
                        }
                    });
        }
    }

    private WorkerThread createWorkerThreadInternal(final ForkJoinPool forkJoinPool, final ContextHandle contextHandleForSetup) {
        WorkerThread newThread = new WorkerThread(forkJoinPool, contextHandleForSetup);
        newThread.setPriority(priority);
        return newThread;
    }

    protected void removeThread(ManagedThread t) {
        lock.lock();
        try {
            threads.remove(t);
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Return an array of threads in this ManagedThreadFactoryImpl
     * @return an array of threads in this ManagedThreadFactoryImpl.
     *         It returns null if there is no thread.
     */
    protected Collection<AbstractManagedThread> getThreads() {
        Collection<AbstractManagedThread> result = null;
        lock.lock();
        try {
            if (!threads.isEmpty()) {
                result = new ArrayList<>(threads);
            }
        }
        finally {
            lock.unlock();
        }
        return result;
    }
    public void taskStarting(Thread t, ManagedFutureTask task) {
        if (t instanceof ManagedThread) {
            ManagedThread mt = (ManagedThread) t;
            // called in thread t, so no need to worry about synchronization
            mt.taskStartTime = System.currentTimeMillis();
            mt.task = task;
        }
    }

    public void taskDone(Thread t) {
        if (t instanceof ManagedThread) {
            ManagedThread mt = (ManagedThread) t;
            // called in thread t, so no need to worry about synchronization
            mt.taskStartTime = 0L;
            mt.task = null;
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
        Iterator<AbstractManagedThread> iter = threads.iterator();
        while(iter.hasNext()) {
            AbstractManagedThread t = iter.next();
            try {
               t.shutdown(); // mark threads as shutting down
               t.interrupt();
            } catch (SecurityException ignore) {
            }
        }
      }
      finally {
          lock.unlock();
      }
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
            return createWorkerThread(pool, contextHandleForSetup);
        } finally {
            lock.unlock();
        }
    }

    class WorkerThread extends ForkJoinWorkerThread {
        final ContextHandle contextHandleForSetup;

        public WorkerThread(ForkJoinPool pool, ContextHandle contextHandleForSetup) {
            super(pool);
            this.contextHandleForSetup = contextHandleForSetup;
        }

        @Override
        protected void onStart() {
            super.onStart();
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
                Logger.getLogger("org.glassfish.enterprise.concurrent").log(Level.INFO, ex.toString());
            } catch (Throwable t) {
                Logger.getLogger("org.glassfish.enterprise.concurrent").log(Level.SEVERE, name, t);
            }
        }

    }

    /**
     * ManageableThread to be returned by {@code ManagedThreadFactory.newThread()}
     */
    public class ManagedThread extends AbstractManagedThread {
        final ContextHandle contextHandleForSetup;
        volatile ManagedFutureTask task = null;
        volatile long taskStartTime = 0L;

        public ManagedThread(Runnable target, ContextHandle contextHandleForSetup) {
            super(target);
            setName(name + "-Thread-" + threadIdSequence.incrementAndGet());
            this.contextHandleForSetup = contextHandleForSetup;
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
                Logger.getLogger("org.glassfish.enterprise.concurrent").log(Level.INFO, ex.toString());
            } catch (Throwable t) {
                Logger.getLogger("org.glassfish.enterprise.concurrent").log(Level.SEVERE, name, t);
            } finally {
                if (handle != null) {
                    contextSetupProvider.reset(handle);
                }
                removeThread(this);
            }
        }

        @Override
        boolean cancelTask() {
            if (task != null) {
                return task.cancel(true);
            }
            return false;
        }

        @Override
        public String getTaskIdentityName() {
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
        boolean isTaskHung(long now) {
            if (hungTaskThreshold > 0) {
                return getTaskRunTime(now) - hungTaskThreshold > 0;
            }
            return false;
        }

    }
}
