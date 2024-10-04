/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 Payara Foundation and/or its affiliates.
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

import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import jakarta.enterprise.concurrent.LastExecution;
import jakarta.enterprise.concurrent.SkippedException;
import jakarta.enterprise.concurrent.Trigger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.glassfish.enterprise.concurrent.AbstractManagedExecutorService;
import org.glassfish.enterprise.concurrent.AbstractManagedThread;

/**
 * ThreadPoolExecutor for running tasks submitted to ScheduledManagedExecutorServiceImpl.
 */
public class ManagedScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {

    private long threadLifeTime = 0L; // in seconds

    public ManagedScheduledThreadPoolExecutor(int corePoolSize) {
        super(corePoolSize);
    }

    public ManagedScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
        super(corePoolSize, threadFactory);
    }

    public ManagedScheduledThreadPoolExecutor(int corePoolSize, RejectedExecutionHandler handler) {
        super(corePoolSize, handler);
    }

    public ManagedScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, threadFactory, handler);
    }

    public void setThreadLifeTime(long threadLifeTime) {
        this.threadLifeTime = threadLifeTime;
        if (threadLifeTime > 0) {
            // do not set allowCoreThreadTimeOut(true); as warned by 
            // ScheduledThreadPoolExecutor javadoc
            long keepAliveTime = getKeepAliveTime(TimeUnit.SECONDS);
            if (keepAliveTime == 0 || threadLifeTime < keepAliveTime) {
                setKeepAliveTime(threadLifeTime, TimeUnit.SECONDS);
            }
        }
    }

   /**
     * Sequence number to break scheduling ties, and in turn to
     * guarantee FIFO order among tied entries.
     */
    private static final AtomicLong sequencer = new AtomicLong(0);

    /**
     * Returns current nanosecond time.
     */
    final long now() {
        return System.nanoTime();
    }

    /**
     * Constrains the values of all delays in the queue to be within
     * Long.MAX_VALUE of each other, to avoid overflow in compareTo.
     * This may occur if a task is eligible to be dequeued, but has
     * not yet been, while some other task is added with a delay of
     * Long.MAX_VALUE.
     */
    private long overflowFree(long delay) {
        Delayed head = (Delayed) super.getQueue().peek();
        if (head != null) {
            long headDelay = head.getDelay(TimeUnit.NANOSECONDS);
            if (headDelay < 0 && (delay - headDelay < 0))
                delay = Long.MAX_VALUE + headDelay;
        }
        return delay;
    }

    /**
     * Returns the trigger time of a delayed action.
     */
    private long triggerTime(long delay, TimeUnit unit) {
        return triggerTime(unit.toNanos((delay < 0) ? 0 : delay));
    }
 
    /**
     * Returns the trigger time of a delayed action.
     */
    long triggerTime(long delay) {
        return now() +
            ((delay < (Long.MAX_VALUE >> 1)) ? delay : overflowFree(delay));
    }

    /**
     * Returns true if can run a task given current run state
     * and run-after-shutdown parameters.
     *
     * @param periodic true if this task periodic, false if delayed
     */
    boolean canRunInCurrentRunState(boolean periodic) {
        // Can only run in RUNNING state
        return !isShutdown();
//        return isRunningOrShutdown(periodic ?
//                                   getContinueExistingPeriodicTasksAfterShutdownPolicy() :
//                                   getExecuteExistingDelayedTasksAfterShutdownPolicy());
    }

    /**
     * Same as prestartCoreThread except arranges that at least one
     * thread is started even if corePoolSize is 0.
     */
    void ensurePrestart() {
        if (getCorePoolSize() == 0) {
            setCorePoolSize(1);
        }
        prestartCoreThread();
    }

    /**
     * Requeues a periodic task unless current run state precludes it.
     * Same idea as delayedExecute except drops task rather than rejecting.
     *
     * @param task the task
     */
    void reExecutePeriodic(RunnableScheduledFuture<?> task) {
        if (canRunInCurrentRunState(true)) {
            super.getQueue().add(task);
            if (!canRunInCurrentRunState(true) && remove(task))
                task.cancel(false);
            else
                ensurePrestart();
        }
    }
    
    /**
     * Invokes the rejected execution handler for the given command.
     * Package-protected for use by ScheduledThreadPoolExecutor.
     */
    final void reject(Runnable command) {
        RejectedExecutionHandler handler = getRejectedExecutionHandler();
        if (handler != null) {
            handler.rejectedExecution(command, this);
        }
    }

    /**
     * Main execution method for delayed or periodic tasks.  If pool
     * is shut down, rejects the task. Otherwise adds task to queue
     * and starts a thread, if necessary, to run it.  (We cannot
     * prestart the thread to run the task because the task (probably)
     * shouldn't be run yet,) If the pool is shut down while the task
     * is being added, cancel and remove it if required by state and
     * run-after-shutdown parameters.
     *
     * @param task the task
     */
    private void delayedExecute(ManagedScheduledThreadPoolExecutor.ManagedScheduledFutureTask<?> task) {
        task.submitted();
        if (isShutdown())
            reject(task);
        else {
            super.getQueue().add(task);
            if (isShutdown() &&
                !canRunInCurrentRunState(task.isPeriodic()) &&
                remove(task))
                task.cancel(false);
            else
                ensurePrestart();
        }
    }

    @Override
    public void execute(Runnable command) {
        schedule(command, 0, TimeUnit.NANOSECONDS);
    }

    public <V> ScheduledFuture<V> schedule(AbstractManagedExecutorService executor,
                                       Runnable command,
                                       V result,
                                       long delay,
                                       TimeUnit unit) {
        if (command == null || unit == null) {
            throw new NullPointerException();
        }
        ManagedScheduledThreadPoolExecutor.ManagedScheduledFutureTask<V> t = 
            new ManagedScheduledThreadPoolExecutor.ManagedScheduledFutureTask<>(
                executor,
                command, 
                result,
                triggerTime(delay, unit));
        delayedExecute(t);
        return t;
    }

    public <V> ScheduledFuture<V> schedule(AbstractManagedExecutorService executor,
                                           Callable<V> callable,
                                           long delay,
                                           TimeUnit unit) {
        if (callable == null || unit == null)
            throw new NullPointerException();
        ManagedScheduledThreadPoolExecutor.ManagedScheduledFutureTask<V> t = 
            new ManagedScheduledThreadPoolExecutor.ManagedScheduledFutureTask<>(
                executor,
                callable,
                triggerTime(delay, unit));
        delayedExecute(t);
        return t;
    }

    public ScheduledFuture<?> schedule(AbstractManagedExecutorService executor,
                                       Runnable command,
                                       Trigger trigger) {
        if (command == null)
            throw new NullPointerException();
        return 
            new ManagedScheduledThreadPoolExecutor.TriggerControllerFuture<>(
                executor,
                command,
                null,
                trigger);
    }

    public <V> ScheduledFuture<V> schedule(AbstractManagedExecutorService executor,
                                           Callable<V> callable,
                                           Trigger trigger) {
        if (callable == null )
            throw new NullPointerException();
        return 
            new ManagedScheduledThreadPoolExecutor.TriggerControllerFuture<>(
                executor,
                callable,
                trigger);
    }
    

    public ScheduledFuture<?> scheduleAtFixedRate(AbstractManagedExecutorService executor,
                                                  Runnable command,
                                                  long initialDelay,
                                                  long period,
                                                  TimeUnit unit) {
        if (command == null || unit == null)
            throw new NullPointerException();
        if (period <= 0)
            throw new IllegalArgumentException();
        ManagedScheduledThreadPoolExecutor.ManagedScheduledFutureTask<Void> t =
            new ManagedScheduledThreadPoolExecutor.ManagedScheduledFutureTask<>(
                executor,            
                command,
                null,
                triggerTime(initialDelay, unit),
                unit.toNanos(period));
        delayedExecute(t);
        return t;
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(AbstractManagedExecutorService executor,
                                                     Runnable command,
                                                     long initialDelay,
                                                     long delay,
                                                     TimeUnit unit) {
        if (command == null || unit == null)
            throw new NullPointerException();
        if (delay <= 0)
            throw new IllegalArgumentException();
        ManagedScheduledThreadPoolExecutor.ManagedScheduledFutureTask<Void> t =
            new ManagedScheduledThreadPoolExecutor.ManagedScheduledFutureTask<>(
                executor,
                command,
                null,
                triggerTime(initialDelay, unit),
                unit.toNanos(-delay));
        delayedExecute(t);
        return t;
    }

   @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);

        ManagedScheduledThreadPoolExecutor.ManagedScheduledFutureTask task = (ManagedScheduledThreadPoolExecutor.ManagedScheduledFutureTask) r;
        try {
            task.done(t /*task.getTaskRunException()*/);
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

    public <V> ManagedFutureTask<V> newTaskFor(
            AbstractManagedExecutorService executor,
            Runnable r, 
            V result) {
        return new ManagedScheduledFutureTask<>(executor, r, result, 0L);
    }
    
    public ManagedFutureTask newTaskFor(
            AbstractManagedExecutorService executor,
            Callable callable) {
        return new ManagedScheduledFutureTask(executor, callable, 0L);
    }

    public void executeManagedTask(ManagedFutureTask task) {
        if (task instanceof ManagedScheduledFutureTask) {
            delayedExecute((ManagedScheduledFutureTask)task);
        } else {
            // should not happen
            schedule(task.executor, task, null, 0L, TimeUnit.NANOSECONDS);
        }
    }
    
    /**
     * Adopted from private class 
     * java.util.concurrent.ScheduledThreadPoolExeuctor$ScheduledFutureTask<V>
     * to provide extended functionalities.
     */
    private class ManagedScheduledFutureTask<V>
        extends ManagedFutureTask<V> implements RunnableScheduledFuture<V> {

        /** Sequence number to break ties FIFO */
        protected final long sequenceNumber;

        /** The next task run time in nanoTime units */
        protected long nextRunTime;

        /**
         * Period in nanoseconds for repeating tasks.  A positive
         * value indicates fixed-rate execution.  A negative value
         * indicates fixed-delay execution.  A value of 0 indicates a
         * non-repeating task.
         */
        private final long period;
        
        /** The actual task to be re-enqueued by reExecutePeriodic */
        RunnableScheduledFuture<V> outerTask = this;

        /**
         * Index into delay queue, to support faster cancellation.
         */
        int heapIndex;

        /**
         * Creates a one-shot action with given nanoTime-based execution time.
         */
        ManagedScheduledFutureTask(AbstractManagedExecutorService executor, 
                Runnable r, 
                V result, 
                long ns) {
            this(executor, r, result, ns, 0L);
        }

        /**
         * Creates a one-shot action with given nanoTime-based execution time.
         */
        ManagedScheduledFutureTask(AbstractManagedExecutorService executor, 
                Callable<V> callable, 
                long ns) {
            this(executor, callable, ns, 0L);
        }

        /**
         * Creates a periodic action with given nano time and period.
         */
        ManagedScheduledFutureTask(AbstractManagedExecutorService executor,
                Runnable r, 
                V result, 
                long ns, 
                long period) {
            super(executor, r, result);
            this.nextRunTime = ns;
            this.period = period;
            this.sequenceNumber = sequencer.getAndIncrement();
        }

        public ManagedScheduledFutureTask(AbstractManagedExecutorService executor, Callable callable, long ns, long period) {
            super(executor, callable);
            this.nextRunTime = ns;
            this.period = period;
            this.sequenceNumber = sequencer.getAndIncrement();
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(nextRunTime - now(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            if (other == this) {
                return 0;
            }
            if (other instanceof ManagedScheduledThreadPoolExecutor.ManagedScheduledFutureTask) {
                ManagedScheduledThreadPoolExecutor.ManagedScheduledFutureTask<?> x = (ManagedScheduledThreadPoolExecutor.ManagedScheduledFutureTask<?>)other;
                long diff = nextRunTime - x.nextRunTime;
                if (diff < 0)
                    return -1;
                else if (diff > 0)
                    return 1;
                else if (sequenceNumber < x.sequenceNumber)
                    return -1;
                else
                    return 1;
            }
            long d = (getDelay(TimeUnit.NANOSECONDS) -
                      other.getDelay(TimeUnit.NANOSECONDS));
            return (d == 0) ? 0 : ((d < 0) ? -1 : 1);
        }
        
        @Override
        public boolean equals(Object other) {
            if (other instanceof ManagedScheduledThreadPoolExecutor.ManagedScheduledFutureTask)
            {
                return compareTo((ManagedScheduledFutureTask)other) == 0;
            }
            return false;
        }

        @Override
        public int hashCode() {
            // using same logic as Long.hashCode()
            return (int)(sequenceNumber^(sequenceNumber>>>32));
        }
        
        /**
         * Returns true if this is a periodic (not a one-shot) action.
         *
         * @return true if periodic
         */
        @Override
        public boolean isPeriodic() {
            return period != 0;
        }

        /**
         * Sets the next time to run for a periodic task.
         * @return true if there is a next run time for the periodic task,
         *         false if the periodic task is done and no need to be scheduled again.
         */
        private void setNextRunTime() {
            long p = period;
            if (p > 0)
                nextRunTime += p;
            else
                nextRunTime = triggerTime(-p);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled && getRemoveOnCancelPolicy() && heapIndex >= 0) {
                remove(this);
            }
            return cancelled;
        }

        /**
         * Overrides FutureTask version so as to reset/requeue if periodic.
         */
        @Override
        public void run() {
            boolean periodic = isPeriodic();
            if (!canRunInCurrentRunState(periodic)) {
                cancel(false);
            }
            else if (!periodic) {
                ManagedScheduledThreadPoolExecutor.ManagedScheduledFutureTask.super.run();
            }
            else if (ManagedScheduledThreadPoolExecutor.ManagedScheduledFutureTask.super.runAndReset()) {
                    setNextRunTime();
                    reExecutePeriodic(outerTask);
            }
        }
    }
    
    /**
     * Represents one task scheduled by TriggerControllerFuture
     */
    private class ManagedTriggerSingleFutureTask<V>
        extends ManagedScheduledFutureTask<V> {

        private TriggerControllerFuture controller;
        private final long scheduledRunTime;
        
        ManagedTriggerSingleFutureTask(AbstractManagedExecutorService executor, 
                                 Callable<V> callable,
                                 long ns,
                                 long scheduledRunTime,
                                 TriggerControllerFuture controller) {
            super(executor, callable, ns);
            this.controller = controller;
            this.scheduledRunTime = scheduledRunTime;
        }

        ManagedTriggerSingleFutureTask(AbstractManagedExecutorService executor, 
                                 Runnable r,
                                 long ns,
                                 long scheduledRunTime,
                                 TriggerControllerFuture controller) {
            super(executor, r, null, ns);
            this.controller = controller;
            this.scheduledRunTime = scheduledRunTime;
        }
        
        private long getDelayFromDate(Date nextRunTime) {
            return triggerTime(nextRunTime.getTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }
        
        @Override
        public boolean isPeriodic() {
            return false;
        }

        @Override
        public void run() {
            if (controller.skipRun(new Date(scheduledRunTime))) {
                return;
            }
            long lastRunStartTime = System.currentTimeMillis();
            Object lastResult = null;
            try {
                super.run();
                lastResult = get();
            } catch (Throwable t) {             
            }
            long lastRunEndTime = System.currentTimeMillis();
            controller.doneExecution(lastResult, 
                    scheduledRunTime, lastRunStartTime, lastRunEndTime);
        }

        @Override
        public void starting(Thread t) {
            controller.starting(t);
        }

    }
    
    /**
     * Future that is returned by schedule with Trigger methods.
     * It is responsible for periodically submitting tasks until 
     * Trigger.getNextRunTime() returns null
     * 
     * @param <V> 
     */
    private class TriggerControllerFuture<V> extends ManagedFutureTask<V> implements ScheduledFuture<V> {

        private final Trigger trigger;
        private final Date taskScheduledTime;
        private final Callable callable;
        private volatile ManagedTriggerSingleFutureTask<V> currentFuture;
        private volatile LastExecution lastExecution;
        private boolean skipped;
        private ReentrantLock lock = new ReentrantLock();

        public TriggerControllerFuture(AbstractManagedExecutorService executor, Callable<V> callable, Trigger trigger) {
            super(executor, callable);
            this.trigger = trigger;
            this.callable = callable;
            this.taskScheduledTime = new Date(System.currentTimeMillis());
            scheduleNextRun();
            submitted();
        }

        public TriggerControllerFuture(AbstractManagedExecutorService executor, Runnable runnable, V result, Trigger trigger) {
            super(executor, runnable, result);
            this.trigger = trigger;
            this.callable = Executors.callable(runnable);
            this.taskScheduledTime = new Date(System.currentTimeMillis());
            scheduleNextRun();
            submitted();
        }

        private void scheduleNextRun() {
            if (isDone()) {
                return;
            }
            Date nextRunTime = trigger.getNextRunTime(lastExecution, taskScheduledTime);
            if (nextRunTime == null) {
                // no more tasks to run for this Trigger
                done(null); // this will call task listeners
                set(null); // to update status of this Future to RAN
                return;
            }
            long ns = triggerTime(nextRunTime.getTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            try {
                lock.lock();
                ManagedTriggerSingleFutureTask<V> future = 
                        new ManagedTriggerSingleFutureTask(executor, callable, ns, nextRunTime.getTime(), this);
                delayedExecute(future);
                currentFuture = future;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            super.cancel(mayInterruptIfRunning);
            // cancel the next scheduled task if there is one
            ManagedTriggerSingleFutureTask<V> future = getCurrentFuture();
            if (future != null) {
                boolean alreadyDone = future.isDone();
                //  return true if the currentFuture is "Completed normally"
                return future.cancel(mayInterruptIfRunning) || alreadyDone;
            }
            return true;
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            if (skipped) {
                throw new SkippedException();
            }
            return getCurrentFuture().get();
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            if (skipped) {
                throw new SkippedException();
            }
            return getCurrentFuture().get(timeout, unit);
        }

        boolean skipRun(Date scheduledRunTime) {
            boolean skip = trigger.skipRun(lastExecution, scheduledRunTime);
            if (skip) {
                // schedule the next run
                scheduleNextRun();
            }
            // set skipped state
            skipped = skip;
            return skip;
        }

        void doneExecution(V result, long scheduledStart, long runStart, long runEnd) {
            lastExecution = new LastExecutionImpl(result, scheduledStart,
                    runStart, runEnd);
            // schedule next run
            scheduleNextRun();
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return getCurrentFuture().getDelay(unit);
        }

        @Override
        public int compareTo(Delayed o) {
            return getCurrentFuture().compareTo(o);
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof TriggerControllerFuture) {
                return compareTo((TriggerControllerFuture)other) == 0;
            }
            return false;
        }
        
        private ManagedTriggerSingleFutureTask<V> getCurrentFuture() {
            try {
                lock.lock();
                return currentFuture;
            } finally {
                lock.unlock();
            }
        }
        
        private class LastExecutionImpl<V> implements LastExecution {

            private V result;
            private ZonedDateTime scheduledStart, runStart, runEnd;

            public LastExecutionImpl(V result, long scheduledStart,
                    long runStart, long runEnd) {
                this.result = result;
                this.scheduledStart = scheduledStart == 0L ? null : ZonedDateTime.ofInstant(Instant.ofEpochMilli(scheduledStart), ZoneId.systemDefault());
                this.runStart = runStart == 0L ? null : ZonedDateTime.ofInstant(Instant.ofEpochMilli(runStart), ZoneId.systemDefault());
                this.runEnd = runEnd == 0L ? null : ZonedDateTime.ofInstant(Instant.ofEpochMilli(runEnd), ZoneId.systemDefault());
            }

            @Override
            public String getIdentityName() {
                return Util.getIdentityName(task);
            }

            @Override
            public Object getResult() {
                return result;
            }

            @Override
            public ZonedDateTime getScheduledStart(ZoneId zone) {
                return scheduledStart.withZoneSameInstant(zone);
            }

            @Override
            public ZonedDateTime getRunStart(ZoneId zone) {
                return runStart.withZoneSameInstant(zone);
            }

            @Override
            public ZonedDateTime getRunEnd(ZoneId zone) {
                return runEnd.withZoneSameInstant(zone);
            }
        }
    }

}
