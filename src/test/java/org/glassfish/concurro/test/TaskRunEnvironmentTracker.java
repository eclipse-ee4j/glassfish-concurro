/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
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

package org.glassfish.concurro.test;

import jakarta.enterprise.concurrent.ManagedExecutorService;

import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaskRunEnvironmentTracker {
    protected final ManagedTestTaskListener taskListener;
    protected int taskStartingCount = 0, taskSubmittedCount = 0;
    public ClassLoader taskRunClassLoader = null;
    protected Future<?> taskAbortedFuture = null, taskDoneFuture = null;
    protected ManagedExecutorService taskStartingExecutor = null,
            taskSubmittedExecutor = null;
    protected int threadPriority = 0;
    protected boolean daemonThread = false;

    public TaskRunEnvironmentTracker(ManagedTestTaskListener taskListener) {
        this.taskListener = taskListener;
    }

    public void verifyAfterRun(String classloaderName, int priority, boolean daemon) {
        assertEquals(priority, threadPriority);
        assertEquals(daemon, daemonThread);
        verifyAfterRun(classloaderName);
    }

    public void verifyAfterRun(String classloaderName) {
        verifyAfterRun(classloaderName, true);
    }

    public void verifyAfterRun(String classloaderName, boolean checksTaskListener) {
        // verify taskListener counters taken within the run() method
        if (checksTaskListener && taskListener != null) {
            assertEquals(1, taskStartingCount);
            assertEquals(1, taskSubmittedCount);
            assertNull(taskDoneFuture);
            assertNull(taskAbortedFuture);
        }
        assertTrue(taskRunClassLoader instanceof NamedClassLoader);
        assertEquals(classloaderName, ((NamedClassLoader) taskRunClassLoader).getName());
    }

    protected void captureThreadContexts() {
        taskRunClassLoader = Thread.currentThread().getContextClassLoader();
        threadPriority = Thread.currentThread().getPriority();
        daemonThread = Thread.currentThread().isDaemon();
        if (taskListener != null) {

            Future<?> taskStartingFuture = taskListener.startingFuture;
            if (taskStartingFuture != null) {
                ManagedTestTaskListener.CallbackParameters starting =
                        taskListener.find(taskStartingFuture, taskListener.STARTING);
                taskStartingExecutor = starting.getExecutor();
                taskStartingCount = starting.getCount();
            }

            Future<?> taskSubmittedFuture = taskListener.submittedFuture;
            if (taskSubmittedFuture != null) {
                ManagedTestTaskListener.CallbackParameters submitting =
                        taskListener.find(taskStartingFuture, taskListener.SUBMITTED);
                taskSubmittedExecutor = submitting.getExecutor();
                taskSubmittedCount = submitting.getCount();
            }

            taskDoneFuture = taskListener.doneFuture;
            taskAbortedFuture = taskListener.abortedFuture;
        }
    }

}
