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

import jakarta.enterprise.concurrent.LastExecution;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.concurrent.SkippedException;
import jakarta.enterprise.concurrent.Trigger;

import java.lang.System.Logger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.glassfish.concurro.AbstractManagedExecutorService.RejectPolicy;
import org.glassfish.concurro.spi.ContextSetupProvider;
import org.glassfish.concurro.test.ClassloaderContextSetupProvider;
import org.glassfish.concurro.test.FakeCallableForTest;
import org.glassfish.concurro.test.FakeRunnableForTest;
import org.glassfish.concurro.test.ManagedBlockingRunnableTask;
import org.glassfish.concurro.test.ManagedCallableTestTask;
import org.glassfish.concurro.test.ManagedRunnableTestTask;
import org.glassfish.concurro.test.ManagedTestTaskListener;
import org.glassfish.concurro.test.ManagedTimeRecordingRunnableTask;
import org.glassfish.concurro.test.TestContextService;
import org.glassfish.concurro.test.TimeRecordingTestRunnable;
import org.glassfish.concurro.test.TimeRecordingTestCallable;
import org.glassfish.concurro.test.Util;
import org.junit.jupiter.api.Test;

import static java.lang.System.Logger.Level.INFO;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


public class ManagedScheduledExecutorServiceAdapterTest extends ManagedExecutorServiceAdapterTest {
    private static final Logger LOG = System.getLogger(ManagedScheduledExecutorServiceAdapterTest.class.getName());

    /**
     * verifies that task got run when scheduled using schedule(Callable, delay, unit)
     */
    @Test
    public void testSchedule_Callable_delay() throws Exception {
        final String classloaderName = "testSchedule_Callable_delay" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String result = "result" + new Date(System.currentTimeMillis());
        FakeCallableForTest<String> task = new FakeCallableForTest<>(result, null);
        ManagedScheduledExecutorService instance =
                createManagedScheduledExecutor("testSchedule_Callable_delay", contextCallback);
        ScheduledFuture<String> future = instance.schedule(task, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertThat(delay, lessThan(1000L));
        assertEquals(result, future.get(10, TimeUnit.SECONDS));
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        // verify context is setup for task
        task.verifyAfterRun(classloaderName);
    }

    /**
     * verifies that task got run when scheduled using schedule(Callable, delay, unit, taskListener)
     */
    @Test
    public void testSchedule_Callable_delay_withListener() throws Exception {
        final String classloaderName = "testSchedule_Callable_delay_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String result = "result" + new Date(System.currentTimeMillis());
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        FakeCallableForTest<String> task = new ManagedCallableTestTask<>(result, taskListener);
        ManagedScheduledExecutorService instance =
                createManagedScheduledExecutor("testSchedule_Callable_delay_withListener", contextCallback);
        ScheduledFuture<String> future = instance.schedule(task, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertThat(delay, lessThan(1000L));
        assertEquals(result, future.get(10, TimeUnit.SECONDS));
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null, classloaderName);
    }

    /**
     * verifies that task got run when scheduled using schedule(Callable, delay, unit, taskListener)
     * with task throwing exception
     */
    @Test
    public void testSchedule_Callable_delay_exception_withListener() throws Exception {
        final String classloaderName = "testSchedule_Callable_delay_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String result = "result" + new Date(System.currentTimeMillis());
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        FakeCallableForTest<String> task = new ManagedCallableTestTask<>(result, taskListener);
        task.setThrowsException(true);
        ManagedScheduledExecutorService instance =
                createManagedScheduledExecutor("testSchedule_Callable_delay_withListener", contextCallback);
        ScheduledFuture<String> future = instance.schedule(task, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay < 1000);
        ExecutionException exception = null;
        try {
            assertEquals(result, future.get(10, TimeUnit.SECONDS));
            fail();
        } catch (ExecutionException ex) {
            exception = ex;
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertNotNull(exception);
        assertEquals(result, exception.getCause().getMessage());
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null, classloaderName);
    }


    /**
     * verifies that task run is delayed when scheduled using schedule(Callable, delay, unit)
     */
    @Test
    public void testSchedule_Callable_long_delay() throws Exception {
        final String classloaderName = "testSchedule_Callable_long_delay" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String result = "result" + new Date(System.currentTimeMillis());
        FakeCallableForTest<String> task = new FakeCallableForTest<>(result, null);
        ManagedScheduledExecutorService instance =
                createManagedScheduledExecutor("testSchedule_Callable_long_delay", contextCallback);
        ScheduledFuture<String> future = instance.schedule(task, 1L, TimeUnit.HOURS);
        long delay = future.getDelay(TimeUnit.SECONDS);
        assertTrue(delay > 0 && delay < 36000);
        assertThrows(TimeoutException.class, () -> future.get(1L, TimeUnit.SECONDS));
        assertFalse(future.isDone());

        // cancel the scheduled task
        future.cancel(true);
    }

    /**
     * verifies that task got run when scheduled using schedule(Runnable, delay, unit)
     */
    @Test
    public void testSchedule_Runnable_delay() throws Exception {
        final String classloaderName = "testSchedule_Runnable_delay" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        FakeRunnableForTest task = new FakeRunnableForTest(null);
        ManagedScheduledExecutorService instance =
                createManagedScheduledExecutor("testSchedule_Runnable_delay", contextCallback);
        ScheduledFuture future = instance.schedule(task, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay < 1000);
        assertNull(future.get(10, TimeUnit.SECONDS));
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName); // verify context is setup for task
    }

    /**
     * verifies that task got run when scheduled using schedule(Runnable, delay, unit, taskListener)
     */
    @Test
    public void testSchedule_Runnable_delay_withListener() throws Exception {
        final String classloaderName = "testSchedule_Runnable_delay_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        FakeRunnableForTest task = new ManagedRunnableTestTask(taskListener);
        ManagedScheduledExecutorService instance =
                createManagedScheduledExecutor("testSchedule_Runnable_delay_withListener", contextCallback);
        ScheduledFuture future = instance.schedule(task, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay < 1000);
        assertNull(future.get());
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null, classloaderName);
    }

    /**
     * verifies that task got run when scheduled using schedule(Runnable, delay, unit, taskListener)
     * with task throwing exception
     */
    @Test
    public void testSchedule_Runnable_delay_exception_withListener() throws Exception {
        final String classloaderName = "testSchedule_Runnable_delay_exception_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final RuntimeException result = new RuntimeException("result" + new Date(System.currentTimeMillis()));
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        FakeRunnableForTest task = new ManagedRunnableTestTask(taskListener, result);
        ManagedScheduledExecutorService instance =
                createManagedScheduledExecutor("testSchedule_Runnable_delay_exception_withListener", contextCallback);
        ScheduledFuture future = instance.schedule(task, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay < 1000);
        ExecutionException exception = null;
        try {
            future.get();
            fail();
        } catch (ExecutionException ex) {
            exception = ex;
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertNotNull(exception);
        assertEquals(result, exception.getCause());
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null, classloaderName);
    }

    /**
     * verifies that task run is delayed when scheduled using schedule(Runnable, delay, unit)
     */
    @Test
    public void testSchedule_Runnable_long_delay() throws Exception {
        final String classloaderName = "testSchedule_Runnable_long_delay" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String result = "result" + new Date(System.currentTimeMillis());
        FakeRunnableForTest task = new FakeRunnableForTest(null);
        ManagedScheduledExecutorService instance =
                createManagedScheduledExecutor("testSchedule_Runnable_long_delay", contextCallback);
        ScheduledFuture future = instance.schedule(task, 1L, TimeUnit.HOURS);
        long delay = future.getDelay(TimeUnit.SECONDS);
        assertTrue(delay > 0 && delay < 36000);
        try {
            future.get(1L, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            // expected
        }
        assertFalse(future.isDone());

        // cancel the scheduled task
        future.cancel(true);
    }

    @Test
    public void testScheduleAtFixedRate() throws Exception {
        final String classloaderName = "testScheduleAtFixedRate" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        TimeRecordingTestRunnable task = new TimeRecordingTestRunnable(null);
        ManagedScheduledExecutorService instance =
                createManagedScheduledExecutor("testScheduleAtFixedRate", contextCallback);
        ScheduledFuture future = instance.scheduleAtFixedRate(task, 2L, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay <= 2000);
        try {
           LOG.log(INFO, "sleeping for 4 seconds");
            // wait 4 seconds, task should have triggered at 2s, 3s, 4s etc
            assertNull(future.get(4, TimeUnit.SECONDS));
        } catch (TimeoutException ex) {
            // expected
        }
        future.cancel(true); // cancel the task
        ArrayList<Long> timeouts = task.getInvocations();
        long submitTime = timeouts.get(0);
        long firstTrigger = timeouts.get(1);
        // firstTrigger should be the initial delay, ie, 2 seconds after submission
        // (consider the test pass if it is between 1 - 3 seconds) - is this assumption ok?
        assertTrue((firstTrigger - submitTime) > 1000 && (firstTrigger - submitTime < 3000));

        long secondTrigger = timeouts.get(2);
        // secondTrigger should 1 second after firstTrigger
        // (consider the test pass if it is between 0 - 2 seconds) - is this assumption ok?
        assertTrue((secondTrigger - firstTrigger) > 0 && (secondTrigger - firstTrigger < 2000));

        for (Long timeout: timeouts) {
            LOG.log(INFO, "timeout: " + Instant.ofEpochMilli(timeout));
        }
        task.verifyAfterRun(classloaderName); // verify context is setup for task
    }

    @Test
    public void testScheduleAtFixedRate_withListener() throws Exception {
        final String classloaderName = "testScheduleAtFixedRate_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        TimeRecordingTestRunnable task = new ManagedTimeRecordingRunnableTask(taskListener);
        ManagedScheduledExecutorService instance =
                createManagedScheduledExecutor("testScheduleAtFixedRate_withListener", contextCallback);
        ScheduledFuture future = instance.scheduleAtFixedRate(task, 2L, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay <= 2000);
        try {
            LOG.log(INFO, "sleeping for 4 seconds");
            // wait 4 seconds, task should have triggered at 2s, 3s, 4s etc
            assertNull(future.get(4, TimeUnit.SECONDS));
        } catch (TimeoutException ex) {
            // expected
        }
        future.cancel(true); // cancel the task
        ArrayList<Long> timeouts = task.getInvocations();
        long submitTime = timeouts.get(0);
        long firstTrigger = timeouts.get(1);
        // firstTrigger should be the initial delay, ie, 2 seconds after submission
        // (consider the test pass if it is between 1 - 3 seconds) - is this assumption ok?
        assertTrue( (firstTrigger - submitTime) > 1000 && (firstTrigger - submitTime < 3000));

        long secondTrigger = timeouts.get(2);
        // secondTrigger should 1 second after firstTrigger
        // (consider the test pass if it is between 0 - 2 seconds) - is this assumption ok?
        assertTrue( (secondTrigger - firstTrigger) > 0 && (secondTrigger - firstTrigger < 2000));

        for (Long timeout: timeouts) {
            LOG.log(INFO, "timeout: " + Instant.ofEpochMilli(timeout));
        }
        task.verifyAfterRun(classloaderName, false); // verify context is setup for task

        // task submitted event should be called only once
        assertEquals(1, taskListener.getCount(future, ManagedTestTaskListener.SUBMITTED));
        // task should be called at least 2 times, verify that listener events
        // also got called at least 2 times
        assertTrue(taskListener.getCount(future, ManagedTestTaskListener.STARTING) >= 2);
        assertTrue(taskListener.getCount(future, ManagedTestTaskListener.DONE) >= 2);

        taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null, classloaderName);
    }

    @Test
    public void testScheduleWithFixedDelay() throws Exception {
        final String classloaderName = "testScheduleWithFixedDelay" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        TimeRecordingTestRunnable task = new TimeRecordingTestRunnable(null);
        ManagedScheduledExecutorService instance =
                createManagedScheduledExecutor("testScheduleWithFixedDelay", contextCallback);
        ScheduledFuture future = instance.scheduleWithFixedDelay(task, 1L, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay <= 1000);
        try {
            LOG.log(INFO, "sleeping for 4 seconds");
            // wait 4 seconds, task should have triggered at 1s, 3s, 5s etc since
            // each task took 1 second to run (due to the sleep() call)
            assertNull(future.get(4, TimeUnit.SECONDS));
        } catch (TimeoutException ex) {
            // expected
        }
        future.cancel(true); // cancel the task
        ArrayList<Long> timeouts = task.getInvocations();
        long submitTime = timeouts.get(0);
        long firstTrigger = timeouts.get(1);
        // firstTrigger should be the initial delay, ie, 1 second after submission
        // (consider the test pass if it is between 0 - 2 seconds) - is this assumption ok?
        assertTrue( (firstTrigger - submitTime) > 0 && (firstTrigger - submitTime < 2000));

        long secondTrigger = timeouts.get(2);
        // secondTrigger should 2 seconds after firstTrigger
        // (consider the test pass if it is between 1 - 3 seconds) - is this assumption ok?
        assertTrue( (secondTrigger - firstTrigger) > 1000 && (secondTrigger - firstTrigger < 3000));

        for (Long timeout: timeouts) {
            LOG.log(INFO, "timeout: " + Instant.ofEpochMilli(timeout));
        }
        task.verifyAfterRun(classloaderName); // verify context is setup for task
    }

    @Test
    public void testScheduleWithFixedDelay_withListener() throws Exception {
        final String classloaderName = "testScheduleWithFixedDelay_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        TimeRecordingTestRunnable task = new ManagedTimeRecordingRunnableTask(taskListener);
        ManagedScheduledExecutorService instance =
                createManagedScheduledExecutor("testScheduleWithFixedDelay_withListener", contextCallback);
        ScheduledFuture future = instance.scheduleWithFixedDelay(task, 1L, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay <= 1000);
        try {
            LOG.log(INFO, "sleeping for 4 seconds");
            // wait 4 seconds, task should have triggered at 1s, 3s, 5s etc since
            // each task took 1 second to run (due to the sleep() call)
            assertNull(future.get(4, TimeUnit.SECONDS));
        } catch (TimeoutException ex) {
            // expected
        }
        future.cancel(true); // cancel the task
        ArrayList<Long> timeouts = task.getInvocations();
        long submitTime = timeouts.get(0);
        long firstTrigger = timeouts.get(1);
        // firstTrigger should be the initial delay, ie, 1 second after submission
        // (consider the test pass if it is between 0 - 2 seconds) - is this assumption ok?
        assertTrue( (firstTrigger - submitTime) > 0 && (firstTrigger - submitTime < 2000));

        long secondTrigger = timeouts.get(2);
        // secondTrigger should 2 seconds after firstTrigger
        // (consider the test pass if it is between 1 - 3 seconds) - is this assumption ok?
        assertTrue( (secondTrigger - firstTrigger) > 1000 && (secondTrigger - firstTrigger < 3000));

        for (Long timeout: timeouts) {
            LOG.log(INFO, "timeout: " + Instant.ofEpochMilli(timeout));
        }
        task.verifyAfterRun(classloaderName, false); // verify context is setup for task

        // task submitted event should be called only once
        assertEquals(1, taskListener.getCount(future, ManagedTestTaskListener.SUBMITTED));
        // task should be called at least 2 times, verify that listener events
        // also got called at least 2 times
        assertTrue(taskListener.getCount(future, ManagedTestTaskListener.STARTING) >= 2);
        assertTrue(taskListener.getCount(future, ManagedTestTaskListener.DONE) >= 2);

        taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null, classloaderName);
    }

    /**
     *
     */
    @Test
    public void testSchedule_Runnable_trigger() throws Exception {
        final String classloaderName = "testSchedule_Runnable_trigger" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        TimeRecordingTestRunnable task = new TimeRecordingTestRunnable(taskListener);
        ManagedScheduledExecutorService instance =
                createManagedScheduledExecutor("testSchedule_Runnable_trigger", contextCallback);
        // +1s, -1s means timeout as it goes backwards, +1 means 1-1+1 about the same time as first
        TestTriggerImpl trigger = new TestTriggerImpl(1, -1, 1);
        ScheduledFuture future = instance.schedule(task, trigger);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay > 500 && delay < 1500);
        boolean gotSkippedException = false;
        boolean gotTimeoutException = false;
        while(!future.isDone()) {
            try {
                future.get(100, TimeUnit.MILLISECONDS);
            } catch (SkippedException se) {
                gotSkippedException = true;
            } catch (TimeoutException e) {
                gotTimeoutException = true;
            }
        }
        assertTrue(gotSkippedException);
        assertTrue(gotTimeoutException);
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName, false); // verify context is setup for task
        assertNull(future.get());
        ArrayList<Long> timeouts = task.getInvocations();
        for (Long timeout: timeouts) {
            LOG.log(INFO, "timeout: " + Instant.ofEpochMilli(timeout));
        }
        taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null, classloaderName);
        taskListener.verifyCallback(ManagedTestTaskListener.DONE, future, instance, task, null, classloaderName);
        assertFalse(taskListener.eventCalled(future, ManagedTestTaskListener.ABORTED));
   }

    @Test
    public void testSchedule_Runnable_trigger_exception() throws Exception {
        final String classloaderName = "testSchedule_Runnable_trigger_exception" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final RuntimeException expectedException = new RuntimeException("MyExpectedException");
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        TimeRecordingTestRunnable task = new TimeRecordingTestRunnable(taskListener, expectedException);
        ManagedScheduledExecutorService instance =
                createManagedScheduledExecutor("testSchedule_Runnable_trigger_exception", contextCallback);
        // +1s, -1s means timeout as it goes backwards, +1 means 1-1+1 about the same time as first
        TestTriggerImpl trigger = new TestTriggerImpl(1, -1, 1);
        ScheduledFuture future = instance.schedule(task, trigger);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay > 500 && delay < 1500);
        boolean gotSkippedException = false;
        boolean gotTimeoutException = false;
        while(!future.isDone()) {
            try {
                future.get(100, TimeUnit.MILLISECONDS);
                fail("expected exception not thrown");
            } catch (SkippedException se) {
                gotSkippedException = true;
            } catch (TimeoutException e) {
                gotTimeoutException = true;
            } catch (ExecutionException e) {
                assertTrue(expectedException == e.getCause());
            }
        }
        assertTrue(gotSkippedException);
        assertTrue(gotTimeoutException);
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName, false); // verify context is setup for task
        Exception exception = null;
        try {
            future.get();
            fail("expected exception not thrown");
        } catch (ExecutionException ex) {
            exception = ex;
        }
        assertNotNull(exception);
        assertTrue(expectedException == exception.getCause());
        ArrayList<Long> timeouts = task.getInvocations();
        for (Long timeout: timeouts) {
            LOG.log(INFO, "timeout: " + Instant.ofEpochMilli(timeout));
        }
        taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null, classloaderName);
        taskListener.verifyCallback(ManagedTestTaskListener.DONE, future, instance, task, null, classloaderName);
        assertFalse(taskListener.eventCalled(future, ManagedTestTaskListener.ABORTED));
    }

    @Test
    public void testSchedule_Callable_trigger() throws Exception {
        final String classloaderName = "testSchedule_Callable_trigger" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String result = "result" + new Date(System.currentTimeMillis());
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        TimeRecordingTestCallable<String> task = new TimeRecordingTestCallable<>(result, taskListener);
        ManagedScheduledExecutorService instance =
                createManagedScheduledExecutor("testSchedule_Callable_trigger", contextCallback);
        // +1s, -1s means timeout as it goes backwards, +1 means 1-1+1 about the same time as first
        TestTriggerImpl trigger = new TestTriggerImpl(1, -1, 1);
        ScheduledFuture future = instance.schedule(task, trigger);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay > 500 && delay < 1500);
        boolean gotSkippedException = false;
        boolean gotTimeoutException = false;
        while(!future.isDone()) {
            try {
                future.get(100, TimeUnit.MILLISECONDS);
            } catch (SkippedException se) {
                gotSkippedException = true;
            } catch (TimeoutException e) {
                gotTimeoutException = true;
            }
        }
        assertTrue(gotSkippedException);
        assertTrue(gotTimeoutException);
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName, false); // verify context is setup for task
        assertEquals(result, future.get());
        List<Long> timeouts = task.getInvocations();
        for (Long timeout: timeouts) {
            LOG.log(INFO, "timeout: " + Instant.ofEpochMilli(timeout));
        }
        taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null, classloaderName);
        taskListener.verifyCallback(ManagedTestTaskListener.DONE, future, instance, task, null, classloaderName);
        assertFalse(taskListener.eventCalled(future, ManagedTestTaskListener.ABORTED));
    }

    @Test
    public void testSchedule_Callable_trigger_exception() throws Exception {
        final String classloaderName = "testSchedule_Callable_trigger_exception" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String result = "result" + new Date(System.currentTimeMillis());
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        TimeRecordingTestCallable<String> task = new TimeRecordingTestCallable<>(result, taskListener);
        task.setThrowsException(true);
        ManagedScheduledExecutorService instance =
                createManagedScheduledExecutor("testSchedule_Callable_trigger_exception", contextCallback);
        // +1s, -1s means timeout as it goes backwards, +1 means 1-1+1 about the same time as first
        TestTriggerImpl trigger = new TestTriggerImpl(1, -1, 1);
        ScheduledFuture future = instance.schedule(task, trigger);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay > 500 && delay < 1500);
        boolean gotSkippedException = false;
        boolean gotTimeoutException = false;
        while(!future.isDone()) {
            try {
                future.get(100, TimeUnit.MILLISECONDS);
                fail("expected exception not thrown");
            } catch (SkippedException se) {
                gotSkippedException = true;
            } catch (TimeoutException e) {
                gotTimeoutException = true;
            } catch (ExecutionException e) {
                assertEquals(result, e.getCause().getMessage());
            }
        }
        assertTrue(gotSkippedException);
        assertTrue(gotTimeoutException);
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName, false); // verify context is setup for task
        Exception exception = null;
        try {
            future.get();
            fail("expected exception not thrown");
        } catch (ExecutionException ex) {
            exception = ex;
        }
        assertNotNull(exception);
        assertEquals(result, exception.getCause().getMessage());
        List<Long> timeouts = task.getInvocations();
        for (Long timeout: timeouts) {
            LOG.log(INFO, "timeout: " + Instant.ofEpochMilli(timeout));
        }
        taskListener.verifyCallback(ManagedTestTaskListener.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTestTaskListener.STARTING, future, instance, task, null, classloaderName);
        taskListener.verifyCallback(ManagedTestTaskListener.DONE, future, instance, task, null, classloaderName);
        assertFalse(taskListener.eventCalled(future, ManagedTestTaskListener.ABORTED));
    }

    @Test
    public void testSchedule_trigger_cancel() throws Exception {
        final String classloaderName = "testSchedule_trigger_cancel" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTestTaskListener taskListener = new ManagedTestTaskListener();
        ManagedBlockingRunnableTask task = new ManagedBlockingRunnableTask(taskListener, 60 * 1000L);
        ManagedScheduledExecutorService instance =
                createManagedScheduledExecutor("testSchedule_trigger_exception", contextCallback);
        // +1s, -1s means timeout as it goes backwards, +1 means 1-1+1 about the same time as first
        TestTriggerImpl trigger = new TestTriggerImpl(1, -1, 1);
        ScheduledFuture future = instance.schedule(task, trigger);
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        assertTrue( future.cancel(true) );

        assertTrue(future.isDone());
        assertTrue(future.isCancelled());

        assertTrue(Util.waitForTaskAborted(future, taskListener), "timeout waiting for taskAborted call");
        taskListener.verifyCallback(ManagedTestTaskListener.ABORTED, future, instance,
                task, new CancellationException());

        // test also that task only executed once as it should have been cancelled.
        Thread.sleep(2000);
        assertEquals(1, taskListener.getCount(future, ManagedTestTaskListener.STARTING),
            "Task executions should be 1 as trigger task was cancelled");
    }


    protected ManagedScheduledExecutorService createManagedScheduledExecutor(String name, ContextSetupProvider contextSetupProvider) {
       return (ManagedScheduledExecutorService) createManagedExecutor(name, contextSetupProvider);
    }

    @Override
    protected ManagedExecutorService createManagedExecutor(String name, ContextSetupProvider contextSetupProvider) {
        ManagedScheduledExecutorServiceImpl mses =
                new ManagedScheduledExecutorServiceImpl(name, null, 0, false,
                    1,
                    0, TimeUnit.SECONDS,
                    0L,
                    new TestContextService(contextSetupProvider),
                    RejectPolicy.ABORT);
        return mses.getAdapter();
    }

    public static class TestTriggerImpl implements Trigger {

        // contains scheduling info for this trigger:
        // +ve number: # of seconds interval after last scheduled run time
        // 0 = return null in getNextRunTime()
        // -ve = skipRun
        int[] intervals;
        int index = 0;
        int offsetTime = 0;
        List<LastExecution> nextRunTimeArgs = new ArrayList<>();
        List<LastExecution> skipRunArgs = new ArrayList<>();

        /**
         * This trigger will return values of now+1s, now+2s, now+3s for getNextRunTime()
         * and will return values of false, true, false for skipRun()
         *
         * @param intervals
         */
        public TestTriggerImpl(int... intervals) {
            this.intervals = intervals;
        }

        @Override
        public Date getNextRunTime(LastExecution lastExecutionInfo, Date taskScheduledTime) {
            nextRunTimeArgs.add(lastExecutionInfo == null? null: new LastExecutionCopy(lastExecutionInfo));
            if (intervals.length > index && intervals[index] != 0) {
                offsetTime += Math.abs(intervals[index]);
                Date nextRunTime = new Date(taskScheduledTime.getTime() + (offsetTime * 1000));
                return nextRunTime;
            }
            return null;
        }

        @Override
        public boolean skipRun(LastExecution lastExecutionInfo, Date scheduledRunTime) {
            skipRunArgs.add(lastExecutionInfo == null? null: new LastExecutionCopy(lastExecutionInfo));
            boolean skip = true;
            if (intervals.length > index && intervals[index] > 0) {
                skip = false;
            }
            index++;
            return skip;
        }
    }

    public static class LastExecutionCopy implements LastExecution {

        String identityName;
        Object result;
        ZonedDateTime scheduledStart, runStart, runEnd;

        public LastExecutionCopy(LastExecution source) {
            if (source != null) {
                identityName = source.getIdentityName();
                result = source.getResult();
                scheduledStart = source.getScheduledStart(ZoneId.systemDefault());
                runStart = source.getRunStart(ZoneId.systemDefault());
                runEnd = source.getRunEnd(ZoneId.systemDefault());
            }
        }

        @Override
        public String getIdentityName() {
            return identityName;
        }

        @Override
        public Object getResult() {
            return result;
        }

        @Override
        public ZonedDateTime getScheduledStart(ZoneId zone) {
            return scheduledStart.withZoneSameLocal(zone);
        }

        @Override
        public ZonedDateTime getRunStart(ZoneId zone) {
            return runStart.withZoneSameLocal(zone);
        }

        @Override
        public ZonedDateTime getRunEnd(ZoneId zone) {
            return runEnd.withZoneSameLocal(zone);
        }

        @Override
        public String toString() {
            return "[LastExecutionInfo] identityName: " + identityName + ", result: " + result + ", scheduledStart: " + scheduledStart
                    + ", runStart: " + runStart + ", runEnd: " + runEnd;
        }

    }
}
