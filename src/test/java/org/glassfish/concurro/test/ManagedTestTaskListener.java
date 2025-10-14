/*
 * Copyright (c) 2023, 2025 Contributors to the Eclipse Foundation.
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024 Payara Foundation and/or its affiliates.
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
import jakarta.enterprise.concurrent.ManagedTaskListener;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ManagedTestTaskListener implements ManagedTaskListener {

    public static final String SUBMITTED = "taskSubmitted", STARTING = "taskStarting",
            DONE = "taskDone", ABORTED = "taskAborted";
    private ConcurrentHashMap<Future, HashMap<String, CallbackParameters>> callParameters
            = new ConcurrentHashMap<>();
    volatile Future<?> startingFuture = null, submittedFuture = null,
            abortedFuture = null, doneFuture = null;

    @Override
    public void taskSubmitted(Future<?> future, ManagedExecutorService executor, Object task) {
        storeEvent(SUBMITTED, future, executor, task, null);
        submittedFuture = future;
    }

    @Override
    public void taskAborted(Future<?> future, ManagedExecutorService executor, Object task, Throwable exception) {
        storeEvent(ABORTED, future, executor, task, exception);
        abortedFuture = future;
    }

    @Override
    public void taskDone(Future<?> future, ManagedExecutorService executor, Object task, Throwable exception) {
        storeEvent(DONE, future, executor, task, exception);
        doneFuture = future;
    }

    @Override
    public void taskStarting(Future<?> future, ManagedExecutorService executor, Object task) {
        storeEvent(STARTING, future, executor, task, null);
        startingFuture = future;
    }

    public boolean eventCalled(Future<?> future, String event) {
        HashMap<String, CallbackParameters> map = callParameters.get(future);
        if (map != null) {
            return map.containsKey(event);
        }
        return false;
    }

    private void storeEvent (String event,
            Future<?> future,
            ManagedExecutorService executor,
            Object task,
            Throwable exception) {
        HashMap<String, CallbackParameters> map = callParameters.get(future);
        if (map == null) {
            map = new HashMap<>();
            callParameters.put(future, map);
        }
        CallbackParameters params = map.get(event);
        if (params != null) {
            params.incrementCount();
        }
        else {
            params = new CallbackParameters(executor, task, exception);
            map.put(event, params);
        }
    }

    /*package*/ CallbackParameters find(Future<?> future, String event) {
        CallbackParameters result = null;
        HashMap<String, CallbackParameters> map = callParameters.get(future);
        if (map != null) {
            result = map.get(event);
        }
        return result;
    }

    public int getCount(Future<?> future, String event) {
        int result = 0;
        CallbackParameters callbackParams = find(future, event);
        if (callbackParams != null) {
            result = callbackParams.getCount();
        }
        return result;
    }

    public Future<?> findFutureWithResult(String result) {
        Set<Future> allFutures = callParameters.keySet();
        for (Future f: allFutures) {
            try {
                if (result.equals(f.get())) {
                    return f;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException ex) {
                // ignore
            } catch (CancellationException ex) {
                // ignore
            }
        }
        return null;
    }

    public void verifyCallback(String event,
            Future<?> future,
            ManagedExecutorService executor,
            Object task,
            Throwable exception) {
        verifyCallback(event, future, executor, task, exception, null);
    }

    public void verifyCallback(String event,
            Future<?> future,
            ManagedExecutorService executor,
            Object task,
            Throwable exception,
            String classloaderName) {
        CallbackParameters result = find(future, event);
        assertNotNull(result, () -> "Callback: '" + event + "' not called");
        assertEquals(executor, result.getExecutor());
        if (task != null) {
            assertTrue(task == result.getTask());
        }
        if (exception == null) {
            assertNull(result.getException());
        }
        else {
            assertNotNull(result.getException(), () -> "expecting exception " + exception + " but none is found");
            assertEquals(exception.getMessage(), result.getException().getMessage());
            assertEquals(exception.getClass(), result.getException().getClass());
        }
        if (classloaderName != null) {
            ClassLoader classLoader = result.getClassLoader();
            assertThat(classLoader, instanceOf(NamedClassLoader.class));
            assertEquals(classloaderName, ((NamedClassLoader)classLoader).getName());
        }
    }

    /*package*/ static class CallbackParameters {
        private ManagedExecutorService executor;
        private Throwable exception;
        private ClassLoader classLoader;
        private Object task;
        private int count;

        public CallbackParameters(ManagedExecutorService executor, Object task, Throwable exception) {
            this.executor = executor;
            this.task = task;
            this.exception = exception;
            this.classLoader = Thread.currentThread().getContextClassLoader();
            this.count = 1;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        public void setClassLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        public int getCount() {
            return count;
        }

        public void incrementCount() {
            this.count++;
        }

        public Object getTask() {
            return task;
        }

        public Throwable getException() {
            return exception;
        }

        public void setException(Throwable exception) {
            this.exception = exception;
        }

        public ManagedExecutorService getExecutor() {
            return executor;
        }

        public void setExecutor(ManagedExecutorService executor) {
            this.executor = executor;
        }

    }
}
