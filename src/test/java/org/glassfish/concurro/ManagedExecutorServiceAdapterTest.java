/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * Copyright (c) 2024 Payara Foundation and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.enterprise.concurrent.AbortedException;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.concurrent.ManagedExecutors;
import jakarta.enterprise.concurrent.ManagedTaskListener;
import org.glassfish.concurro.ContextServiceImpl;
import org.glassfish.concurro.ManagedExecutorServiceImpl;
import org.glassfish.concurro.AbstractManagedExecutorService.RejectPolicy;
import org.glassfish.concurro.spi.ContextSetupProvider;
import org.glassfish.concurro.test.BlockingRunnableForTest;
import org.glassfish.concurro.test.FakeCallableForTest;
import org.glassfish.concurro.test.ClassloaderContextSetupProvider;
import org.glassfish.concurro.test.ManagedBlockingRunnableTask;
import org.glassfish.concurro.test.ManagedCallableTask;
import org.glassfish.concurro.test.ManagedRunnableTestTask;
import org.glassfish.concurro.test.ManagedTestTaskListener;
import org.glassfish.concurro.test.FakeRunnableForTest;
import org.glassfish.concurro.test.TestContextService;
import org.glassfish.concurro.test.Util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class ManagedExecutorServiceAdapterTest  {


    /**
     * Test of execute method, of ManagedExecutorServiceAdapter.
     * Verify context callback are called to setup context
     */
    @Test
    public void testExecute() {
        debug("execute");

        final String classloaderName = "testExcute" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final FakeRunnableForTest task = new FakeRunnableForTest(null);
        ManagedExecutorService instance =
                createManagedExecutor("execute", contextCallback);
        instance.execute(task);
        assertTrue("timeout waiting for task run being called", Util.waitForTaskComplete(task, getLoggerName()));
        task.verifyAfterRun(classloaderName); // verify context is setup for task

        debug("execute done");
    }

    public String getLoggerName() {
        return ManagedExecutorServiceAdapterTest.class.getName();
    }

    /**
     * Test of submit method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testSubmit_Runnable() {
        debug("submit_Runnable");

        final String classloaderName = "testSubmit" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        FakeRunnableForTest task = new FakeRunnableForTest(null);
        ManagedExecutorService instance =
                createManagedExecutor("submit", contextCallback);
        Future future = instance.submit(task);
        try {
            assertNull(future.get());
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName); // verify context is setup for task

        // save original classloader prior to previous run
        ClassLoader cl = contextCallback.classloaderBeforeSetup;

        // reset classloaderBeforeSetup for testing purpose.
        contextCallback.classloaderBeforeSetup = null;

        // submit a second task to verify that
        // - the ContextHandler.reset is called

        FakeRunnableForTest task2 = new FakeRunnableForTest(null);
        future = instance.submit(task2);
        try {
            future.get();
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        verify_testSubmit_Runnable(cl, contextCallback);
    }

    protected void verify_testSubmit_Runnable(ClassLoader originalClassLoader,
            final ClassloaderContextSetupProvider contextCallback) {
        assertEquals(originalClassLoader, contextCallback.classloaderBeforeSetup);
        // reset() should be called at least once for the first task
        // reset() may or may not be called for the second task due to timing
        // issue when this check is performed.
        assertTrue(Util.waitForBoolean(
            new Util.BooleanValueProducer() {
              @Override
            public boolean getValue() {
                return contextCallback.numResetCalled > 1;
              }
            }, true, getLoggerName()));
    }

    /**
     * Test of submit method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testSubmit_Runnable_withListener() {
        debug("submit_Runnable_withListener");

        final String classloaderName = "submit_Runnable_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        FakeRunnableForTest task = new ManagedRunnableTestTask(taskListener);
        ManagedExecutorService instance =
                createManagedExecutor("submit_Runnable_withListener", contextCallback);
        Future future = instance.submit(task);
        try {
            assertNull(future.get());
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null, classloaderName);
        taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);
    }

    protected void verify_testSubmit_Runnable_withListener(
            Future<?> future,
            ClassLoader originalClassLoader,
            ClassloaderContextSetupProvider contextSetupProvider,
            ManagedTestTaskListener taskListener) {
        assertEquals(originalClassLoader, contextSetupProvider.classloaderBeforeSetup);
        // taskAborted should not be called
        assertFalse(taskListener.eventCalled(future, ManagedTestTaskListener.ABORTED));

        // should be called for at least the first task
        assertTrue(contextSetupProvider.numResetCalled > 0);

        // taskDone chould be called for the first task
        assertTrue(taskListener.eventCalled(future, ManagedTestTaskListener.DONE));
    }

    /**
     * Test submit(Runnable) but context setup fails, which could happen if
     * the application component submitting the task is no longer running.
     */
    @Test
    public void testSubmit_Runnable_withListener_invalidContext() {
        debug("submit_Runnable_withListener_invalidContext");

        final String classloaderName = "submit_runnable_withListener_invalidContext" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String MESSAGE = "Invalid Context";
        contextCallback.throwsOnSetup(MESSAGE);

        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        FakeRunnableForTest task = new ManagedRunnableTestTask(taskListener);
        ManagedExecutorService instance =
                createManagedExecutor("submit", contextCallback);
        Future future = instance.submit(task);
        try {
            assertNull(future.get());
            fail("Expected Exception not thrown");
        } catch (ExecutionException ex) {
            assertTrue(ex.getCause() instanceof AbortedException);
            assertEquals(MESSAGE, ex.getCause().getMessage());
        } catch (InterruptedException ex) {
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        taskListener.eventCalled(future, ManagedTestTaskListener.STARTING);
        taskListener.eventCalled(future, ManagedTestTaskListener.DONE);
        taskListener.eventCalled(future, ManagedTestTaskListener.SUBMITTED);
        taskListener.eventCalled(future, ManagedTestTaskListener.ABORTED);
    }

    /**
     * Test of submit method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testSubmit_Runnable_result() {
        debug("submit_Runnable");

        final String classloaderName = "testSubmit" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        FakeRunnableForTest task = new FakeRunnableForTest(null);
        ManagedExecutorService instance =
                createManagedExecutor("submit", contextCallback);
        final String result = "result" + new Date(System.currentTimeMillis());
        Future future = instance.submit(task, result);
        try {
            assertEquals(result, future.get());
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName); // verify context is setup for task

        // save original classloader prior to previous run
        ClassLoader cl = contextCallback.classloaderBeforeSetup;

        // reset classloaderBeforeSetup for testing purpose.
        contextCallback.classloaderBeforeSetup = null;

        // submit a second task to verify that the ContextHandler.reset is called

        FakeRunnableForTest task2 = new FakeRunnableForTest(null);
        future = instance.submit(task2);
        try {
            future.get();
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        verify_testSubmit_Runnable(cl, contextCallback);
    }

    /**
     * Test of submit method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testSubmit_Runnable_result_withListener() {
        debug("submit_Runnable_result_withListener");

        final String classloaderName = "testSubmit" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        FakeRunnableForTest task = new ManagedRunnableTestTask(taskListener);
        ManagedExecutorService instance =
                createManagedExecutor("submit", contextCallback);
        final String result = "result" + new Date(System.currentTimeMillis());
        Future future = instance.submit(task, result);
        try {
            assertEquals(result, future.get());
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null, classloaderName);
        taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);
    }

    /**
     * Test of submit method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testSubmit_Runnable_exception_withListener() {
        debug("submit_Runnable_result_exception_withListener");

        final String classloaderName = "testSubmit" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        final RuntimeException result = new RuntimeException("result" + new Date(System.currentTimeMillis()));
        FakeRunnableForTest task = new ManagedRunnableTestTask(taskListener, result);
        ManagedExecutorService instance =
                createManagedExecutor("submit", contextCallback);
        Future future = instance.submit(task, result);
        ExecutionException executionException = null;
        try {
            future.get();
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            executionException = ex;
        }
        assertNotNull(executionException);
        assertEquals(result, executionException.getCause());
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null, classloaderName);
        taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);
    }

    /**
     * Test of submit method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testSubmit_Callable() {
        debug("submit_Callable");

        final String classloaderName = "testSubmit" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String result = "result" + new Date(System.currentTimeMillis());
        FakeCallableForTest<String> task = new FakeCallableForTest<>(result, null);
        ManagedExecutorService instance =
                createManagedExecutor("submit", contextCallback);
        Future<String> future = instance.submit(task);
        try {
            assertEquals(result, future.get());
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName); // verify context is setup for task

        // save original classloader prior to previous run
        ClassLoader cl = contextCallback.classloaderBeforeSetup;

        // reset classloaderBeforeSetup for testing purpose.
        contextCallback.classloaderBeforeSetup = null;

        // submit a second task to verify that the ContextHandler.reset is called

        FakeCallableForTest<String> task2 = new FakeCallableForTest("result");
        future = instance.submit(task2);
        try {
            future.get();
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        verify_testSubmit_Runnable(cl, contextCallback);
    }

    /**
     * Test of submit method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testSubmit_Callable_withListener() {
        debug("submit_Callable_withListener");

        final String classloaderName = "testSubmit" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        final String result = "result" + new Date(System.currentTimeMillis());
        FakeCallableForTest<String> task = new ManagedCallableTask<>(result, taskListener);
        ManagedExecutorService instance =
                createManagedExecutor("submit", contextCallback);
        Future<String> future = instance.submit(task);
        try {
            assertEquals(result, future.get());
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null);
        taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);
        debug("submit_Callable_withListener done");
    }

    /**
     * Verify that ManagedTaskListener can be made contextual
     */
    @Test
    public void testSubmit_Callable_withContextualListener() {
        debug("submit_Callable_withContextualListener");

        final String taskClassloaderName = "testSubmit-task" + new Date(System.currentTimeMillis());
        final String contextServiceClassloaderName = "testSubmit-contextService" + new Date(System.currentTimeMillis());
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        final String result = "result" + new Date(System.currentTimeMillis());

        ContextServiceImpl contextService =
                new ContextServiceImpl("myContextService",
                                       new ClassloaderContextSetupProvider(contextServiceClassloaderName));
        ManagedTaskListener proxy = contextService.createContextualProxy(taskListener, ManagedTaskListener.class);

        FakeCallableForTest<String> task = new FakeCallableForTest<>(result, taskListener);
        Callable<String> taskWithListener = ManagedExecutors.managedTask(task, proxy);
        ManagedExecutorService instance =
                createManagedExecutor("submit", new ClassloaderContextSetupProvider(taskClassloaderName));
        Future<String> future = instance.submit(taskWithListener);
        try {
            assertEquals(result, future.get());
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(taskClassloaderName); // verify context is setup for task
        Util.waitForTaskDone(future, taskListener, result);
        taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, taskWithListener, null, contextServiceClassloaderName);
        taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, taskWithListener, null, contextServiceClassloaderName);
        taskListener.verifyCallback(ManagedTestTaskListener.DONE, future, instance, taskWithListener, null, contextServiceClassloaderName);
        debug("submit_Callable_withContextualListener done");
    }

    /**
     * Test of submit method
     */
    @Test
    public void testSubmit_Callable_exception_withListener() {
        debug("submit_Callable_exception_withListener");

        final String classloaderName = "testSubmit_Callable_exception_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        final String result = "result" + new Date(System.currentTimeMillis());
        FakeCallableForTest<String> task = new ManagedCallableTask<>(result, taskListener);
        task.setThrowsException(true);
        ManagedExecutorService instance =
                createManagedExecutor("submit", contextCallback);
        Future<String> future = instance.submit(task);
        ExecutionException executionException = null;
        try {
            future.get();
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            executionException = ex;
        }
        assertNotNull(executionException);
        assertEquals(result, executionException.getCause().getMessage());
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null, classloaderName);
        taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);

        debug("submit_Callable_exception_withListener done");
    }

    /**
     * Test submit(Callable) but context setup fails, which could happen if
     * the application component submitting the task is no longer running.
     */
    @Test
    public void testSubmit_Callable_withListener_invalidContext() {
        debug("submit_Runnable_withListener_invalidContext");

        final String classloaderName = "submit_runnable_withListener_invalidContext" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String MESSAGE = "Invalid Context";
        contextCallback.throwsOnSetup(MESSAGE);

        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        final String result = "result" + new Date(System.currentTimeMillis());
       FakeCallableForTest<String> task = new ManagedCallableTask<>(result, taskListener);
        ManagedExecutorService instance =
                createManagedExecutor("submit", contextCallback);
        Future future = instance.submit(task);
        try {
            assertNull(future.get());
            fail("Expected Exception not thrown");
        } catch (ExecutionException ex) {
            assertTrue(ex.getCause() instanceof AbortedException);
            assertEquals(MESSAGE, ex.getCause().getMessage());
        } catch (InterruptedException ex) {
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        taskListener.eventCalled(future, ManagedTestTaskListener.STARTING);
        taskListener.eventCalled(future, ManagedTestTaskListener.DONE);
        taskListener.eventCalled(future, ManagedTestTaskListener.SUBMITTED);
        taskListener.eventCalled(future, ManagedTestTaskListener.ABORTED);
    }

    /**
     * Test of invokeAny method.
     */
    @Test
    public void testInvokeAny() {
        debug("invokeAny");

        final String classloaderName = "testInvokeAny" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ArrayList<FakeCallableForTest<String>> tasks = new ArrayList<>();
        FakeCallableForTest<String> goodTask = null;
        String expectedResult = null;
        for (int i=0; i<10; i++) {
            // set up 10 tasks. 9 of which throws leaving one returning good result.
            String result = "result" + new Date(System.currentTimeMillis());
            FakeCallableForTest<String> task = new FakeCallableForTest<>(result, null);
            tasks.add(task);
            if (i == 5) {
                goodTask = task;
                expectedResult = result;
            } else {
                task.setThrowsException(true);
            }
        }
        ManagedExecutorService instance =
                createManagedExecutor("testInvokeAny", contextCallback);
        try {
            String result = instance.invokeAny(tasks);
            assertEquals(expectedResult, result);
            goodTask.verifyAfterRun(classloaderName); // verify context is setup for task
        } catch (InterruptedException | ExecutionException ex) {
            fail(ex.toString());
        }
    }

    /**
     * Test of invokeAny method where all tasks throw exception
     *
     */
    @Test(expected = ExecutionException.class)
    public void testInvokeAny_exception() throws Exception {
        debug("invokeAny_exception");

        final String classloaderName = "testInvokeAny_exception" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ArrayList<FakeCallableForTest<String>> tasks = new ArrayList<>();
        for (int i=0; i<10; i++) {
            // set up 10 tasks. 9 of which throws leaving one returning good result.
            String result = "result" + new Date(System.currentTimeMillis());
            FakeCallableForTest<String> task = new FakeCallableForTest<>(result, null);
            tasks.add(task);
            task.setThrowsException(true);
        }
        ManagedExecutorService instance =
                createManagedExecutor("testInvokeAny_exception", contextCallback);
        String result = instance.invokeAny(tasks); // expecting ExecutionException
    }

    /**
     * Test of invokeAny method where no tasks completes before timeout
     *
     */
    @Test(expected = TimeoutException.class)
    public void testInvokeAny_timeout() throws Exception {
        debug("invokeAny_timeout");

        final String classloaderName = "testInvokeAny_exception" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ArrayList<Callable<String>> tasks = new ArrayList<>();
        for (int i=0; i<2; i++) {
            // set up 10 tasks. 9 of which throws leaving one returning good result.
            String result = "result" + new Date(System.currentTimeMillis());
            Callable<String> task = Executors.callable(new BlockingRunnableForTest(null, 20000), "done" + i);
            tasks.add(task);
        }
        ManagedExecutorService instance =
                createManagedExecutor("testInvokeAny_timeout", contextCallback);
        String result = instance.invokeAny(tasks, 2, TimeUnit.SECONDS); // expecting TimeoutException
    }

    /**
     * Test of invokeAny method with TaskListener
     */
    //@Ignore
    @Test
    public void testInvokeAny_withListener() {
        debug("invokeAny_withListener");

        final String classloaderName = "testInvokeAny_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ArrayList<FakeCallableForTest<String>> tasks = new ArrayList<>();
        FakeCallableForTest<String> goodTask = null;
        ManagedTestTaskListener listenerForGoodTask = null;

        String expectedResult = null;
        for (int i=0; i<10; i++) {
            // set up 10 tasks. 9 of which throws leaving one returning good result.
            String result = "result" + new Date(System.currentTimeMillis());
            ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
            FakeCallableForTest<String> task = new ManagedCallableTask<>(result, taskListener);
            tasks.add(task);
            if (i == 5) {
                goodTask = task;
                listenerForGoodTask = taskListener;
                expectedResult = result;
            } else {
                task.setThrowsException(true);
            }
        }
        ManagedExecutorService instance =
                createManagedExecutor("testInovokeAny_withListener", contextCallback);
        try {
            String result = instance.invokeAny(tasks);
            assertEquals(expectedResult, result);

            Future<?> future = listenerForGoodTask.findFutureWithResult(result);
            listenerForGoodTask.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, goodTask, null);
            listenerForGoodTask.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, goodTask, null);
            Util.waitForTaskDone(future, listenerForGoodTask, getLoggerName());
            listenerForGoodTask.verifyCallback(ManagedTestTaskListener.DONE, future, instance, goodTask, null);
            assertFalse(listenerForGoodTask.eventCalled(future, ManagedTestTaskListener.ABORTED));
            goodTask.verifyAfterRun(classloaderName, false); // verify context is setup for task
        } catch (InterruptedException | ExecutionException ex) {
            fail(ex.toString());
        }
    }

    /**
     * Test of invokeAny method with TaskListener where all tasks throw exception
     *
     */
    @Test(expected = ExecutionException.class)
    public void testInvokeAny_exception_withListener() throws Exception {
        debug("invokeAny_exception_withListener");

        final String classloaderName = "testInvokeAny_exception_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ArrayList<FakeCallableForTest<String>> tasks = new ArrayList<>();
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        for (int i=0; i<10; i++) {
            // set up 10 tasks. 9 of which throws leaving one returning good result.
            String result = "result" + new Date(System.currentTimeMillis());
            FakeCallableForTest<String> task = new ManagedCallableTask<>(result, taskListener);
            tasks.add(task);
            task.setThrowsException(true);
        }
        ManagedExecutorService instance =
                createManagedExecutor("testInvokeAny_exception_withListener", contextCallback);
        String result = instance.invokeAny(tasks); // expecting ExecutionException
    }

    /**
     * Test of invokeAll method
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testInvokeAll() {
        debug("invokeAll");

        final String classloaderName = "testInvokeAll" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ArrayList<FakeCallableForTest<String>> tasks = new ArrayList<>();
        ArrayList<String>results = new ArrayList<>();
        for (int i=0; i<10; i++) {
            String result = "result" + new Date(System.currentTimeMillis());
            FakeCallableForTest<String> task = new FakeCallableForTest<>(result, null);
            tasks.add(task);
            results.add(result);
        }
        ManagedExecutorService instance =
                createManagedExecutor("testInvokeAll", contextCallback);
        try {
            int resultsIndex = 0;
            List<Future<String>> futures = instance.invokeAll(tasks);
            for (Future<String> future : futures) {
                assertTrue(future.isDone());
                assertFalse(future.isCancelled());
                String result = future.get();
                assertEquals(results.get(resultsIndex++), result);
            }
            for (FakeCallableForTest<String> task : tasks) {
                task.verifyAfterRun(classloaderName); // verify context is setup for task
            }
        } catch (InterruptedException | ExecutionException ex) {
            fail(ex.toString());
        }
    }

    /**
     * Test of invokeAll method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testInvokeAll_withListener() {
        debug("invokeAll_withListener");

        final String classloaderName = "testInvokeAll_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ArrayList<FakeCallableForTest<String>> tasks = new ArrayList<>();
        ArrayList<String>results = new ArrayList<>();
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        for (int i=0; i<10; i++) {
            String result = "result" + new Date(System.currentTimeMillis());
            FakeCallableForTest<String> task = new ManagedCallableTask<>(result, taskListener);
            tasks.add(task);
            results.add(result);
        }
        ManagedExecutorService instance =
                createManagedExecutor("testInovokeAll_withListener", contextCallback);
        try {
            int resultsIndex = 0;
            List<Future<String>> futures = instance.invokeAll(tasks);
            for (int i=0; i<10; i++) {
                Future<String> future = futures.get(i);
                Object task = tasks.get(i);
                assertTrue(future.isDone());
                assertFalse(future.isCancelled());
                String result = future.get();
                assertEquals(results.get(resultsIndex++), result);
                taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null);
                taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);
                Util.waitForTaskDone(future, taskListener, getLoggerName());
                taskListener.verifyCallback(ManagedTestTaskListener.DONE, future, instance, task, null);
                assertFalse(taskListener.eventCalled(future, ManagedTestTaskListener.ABORTED));
            }
            for (FakeCallableForTest<String> task : tasks) {
                task.verifyAfterRun(classloaderName, false); // verify context is setup for task
            }
        } catch (InterruptedException | ExecutionException ex) {
            fail(ex.toString());
        }
    }

    /**
     * Test of invokeAll method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testInvokeAll_exception() {
        debug("invokeAll_exception");

        final String classloaderName = "testInvokeAll_exception" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ArrayList<FakeCallableForTest<String>> tasks = new ArrayList<>();
        ArrayList<String>results = new ArrayList<>();
        for (int i=0; i<10; i++) {
            // Configure 10 tasks. Half of which throws
            String result = "result" + new Date(System.currentTimeMillis());
            FakeCallableForTest<String> task = new FakeCallableForTest<>(result, null);
            if (i % 2 == 0) {
                task.setThrowsException(true);
            }
            tasks.add(task);
            results.add(result);
        }
        ManagedExecutorService instance =
                createManagedExecutor("testInovokeAll_exception", contextCallback);
        try {
            int resultsIndex = 0;
            List<Future<String>> futures = instance.invokeAll(tasks);
            for (Future<String> future : futures) {
                assertTrue(future.isDone());
                assertFalse(future.isCancelled());
                try {
                    String result = future.get();
                    if (resultsIndex % 2 == 0) {
                        fail("Expected exception not thrown for results[" + resultsIndex + "] but got " + result);
                    }
                    assertEquals(results.get(resultsIndex), result);
                }
                catch (ExecutionException ex) {
                    if (resultsIndex % 2 != 0) {
                        fail("exception not expected at results[" + resultsIndex + "]");
                    }
                    assertEquals(results.get(resultsIndex), ex.getCause().getMessage());
                }
                resultsIndex++;
            }
            for (FakeCallableForTest<String> task : tasks) {
                task.verifyAfterRun(classloaderName); // verify context is setup for task
            }
        } catch (InterruptedException ex) {
            fail(ex.toString());
        }
    }

    /**
     * Test of invokeAll method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testInvokeAll_exception_withListener() {
        debug("invokeAll_exception_withListener");

        final String classloaderName = "testInvokeAll_exception_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ArrayList<FakeCallableForTest<String>> tasks = new ArrayList<>();
        ArrayList<String>results = new ArrayList<>();
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        for (int i=0; i<10; i++) {
            String result = "result" + new Date(System.currentTimeMillis());
            FakeCallableForTest<String> task = new ManagedCallableTask<>(result, taskListener);
            if (i % 2 == 0) {
                task.setThrowsException(true);
            }
            tasks.add(task);
            results.add(result);
        }
        ManagedExecutorService instance =
                createManagedExecutor("testInovokeAll_withListener", contextCallback);
        try {
            int resultsIndex = 0;
            List<Future<String>> futures = instance.invokeAll(tasks);
            for (Future<String> future : futures) {
                debug("resultsIndex is " + resultsIndex);
                Object task = tasks.get(resultsIndex);
                assertTrue(future.isDone());
                assertFalse(future.isCancelled());
                try {
                    String result = future.get();
                    if (resultsIndex % 2 == 0) {
                        fail("Expected exception not thrown for results[" + resultsIndex + "] but got " + result);
                    }
                    assertEquals(results.get(resultsIndex), result);
                    taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null);
                    taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);
                    Util.waitForTaskDone(future, taskListener, getLoggerName());
                    taskListener.verifyCallback(ManagedTestTaskListener.DONE, future, instance, task, null);
                }
                catch (ExecutionException ex) {
                    if (resultsIndex % 2 != 0) {
                        fail("exception not expected at results[" + resultsIndex + "]");
                    }
                    taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null);
                    taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);
                    Util.waitForTaskDone(future, taskListener, getLoggerName());
                    taskListener.verifyCallback(ManagedTestTaskListener.DONE,
                            future, instance, task,
                            new Exception(results.get(resultsIndex)));
                }
                assertFalse(taskListener.eventCalled(future, ManagedTestTaskListener.ABORTED));
                resultsIndex++;
            }
            for (FakeCallableForTest<String> eachTask : tasks) {
                eachTask.verifyAfterRun(classloaderName, false); // verify context is setup for task
            }
        } catch (InterruptedException ex) {
            fail(ex.toString());
        }
    }

    /**
     * Test of cancel method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testCancel() {
        debug("cancel");

        final String classloaderName = "testCancel" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        FakeRunnableForTest task = new BlockingRunnableForTest(null, 60 * 1000L);
        ManagedExecutorService instance =
                createManagedExecutor("submit", contextCallback);
        Future future = instance.submit(task);
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        assertTrue( future.cancel(true) );

        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
    }

    /**
     * Test of cancel method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testCancel_withListener() {
        debug("cancel_withListener");

        final String classloaderName = "testCancel" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        FakeRunnableForTest task = new ManagedBlockingRunnableTask(taskListener, 60 * 1000L);
        ManagedExecutorService instance =
                createManagedExecutor("submit", contextCallback);
        Future future = instance.submit(task);
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        assertTrue( future.cancel(true) );

        assertTrue(future.isDone());
        assertTrue(future.isCancelled());

        assertTrue("timeout waiting for taskAborted call", Util.waitForTaskAborted(future, taskListener, getLoggerName()));
        taskListener.verifyCallback(ManagedTestTaskListener.ABORTED, future,
                instance, task,
                new CancellationException());
    }

    @Test(expected = IllegalStateException.class)
    public void testShutdown() {
        ManagedExecutorService mes =
                createManagedExecutor("testShutdown", null);
        mes.shutdown();
    }

    @Test(expected = IllegalStateException.class)
    public void testIsShutdown() {
        ManagedExecutorService mes =
                createManagedExecutor("testShutdown", null);
        mes.isShutdown();
    }

    @Test(expected = IllegalStateException.class)
    public void testIsShutdownNow() {
        ManagedExecutorService mes =
                createManagedExecutor("testShutdownNow", null);
        mes.shutdownNow();
    }

    @Test(expected = IllegalStateException.class)
    public void testIsTerminated() {
        ManagedExecutorService mes =
                createManagedExecutor("isTerminated", null);
        mes.isTerminated();
    }

    @Test(expected = IllegalStateException.class)
    public void testAwaitTermination() throws InterruptedException {
        ManagedExecutorService mes =
                createManagedExecutor("awaitTermination", null);
        mes.awaitTermination(10, TimeUnit.MILLISECONDS);
    }

    protected ManagedExecutorService createManagedExecutor(String name,
            ContextSetupProvider contextSetupProvider) {
        ManagedExecutorServiceImpl mes =
                new ManagedExecutorServiceImpl(name, null, 0, false,
                    1, 1,
                    0, TimeUnit.SECONDS,
                    0L,
                    Integer.MAX_VALUE,
                    new TestContextService(contextSetupProvider),
                    RejectPolicy.ABORT);
        return mes.getAdapter();
    }

    final static boolean DEBUG = false;

    protected void debug(String msg) {
        if (DEBUG) {
            System.out.println(msg);
        }
    }
}
