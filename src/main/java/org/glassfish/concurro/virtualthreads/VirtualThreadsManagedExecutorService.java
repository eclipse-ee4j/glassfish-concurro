/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
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
import jakarta.enterprise.concurrent.ManagedTaskListener;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.glassfish.concurro.AbstractManagedExecutorService;
import org.glassfish.concurro.ContextServiceImpl;
import org.glassfish.concurro.ManagedThreadFactoryImpl;
import org.glassfish.concurro.internal.ManagedFutureTask;

/**
 * Dummy Implementation of ManagedExecutorService interface using Virtual Threads.
 *
 * @author Kalin Chan
 */
public class VirtualThreadsManagedExecutorService extends AbstractManagedExecutorService implements ManagedTaskListener {

    public VirtualThreadsManagedExecutorService(String name,
            VirtualThreadsManagedThreadFactory managedThreadFactory,
            long hungTaskThreshold,
            boolean longRunningTasks,
            int maxParallelTasks,
            int queueCapacity,
            ContextServiceImpl contextService,
            RejectPolicy rejectPolicy) {
        super(null, false,
                null,
                null,
                null);
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }


    @Override
    public void execute(Runnable command) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    protected void executeManagedFutureTask(ManagedFutureTask<?> task) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public List<Runnable> shutdownNow() {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public void shutdown() {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    protected ExecutorService getThreadPoolExecutor() {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    protected boolean isTaskHung(Thread thread, long now) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public ManagedExecutorService getExecutorForTaskListener() {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    protected <V> ManagedFutureTask<V> getNewTaskFor(Runnable r, V result) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    protected <V> ManagedFutureTask<V> getNewTaskFor(Callable<V> callable) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public long getTaskCount() {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public long getCompletedTaskCount() {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public void taskSubmitted(Future<?> future, ManagedExecutorService executor, Object task) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public void taskAborted(Future<?> future, ManagedExecutorService executor, Object task, Throwable exception) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public void taskDone(Future<?> future, ManagedExecutorService executor, Object task, Throwable exception) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public void taskStarting(Future<?> future, ManagedExecutorService executor, Object task) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public ManagedThreadFactoryImpl getManagedThreadFactory() {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public ManagedExecutorService getAdapter() {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }
}
