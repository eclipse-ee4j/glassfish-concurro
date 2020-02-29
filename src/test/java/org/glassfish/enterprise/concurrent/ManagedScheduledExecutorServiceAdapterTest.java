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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.enterprise.concurrent.LastExecution;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.concurrent.SkippedException;
import jakarta.enterprise.concurrent.Trigger;
import org.glassfish.enterprise.concurrent.AbstractManagedExecutorService.RejectPolicy;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;
import org.glassfish.enterprise.concurrent.test.CallableImpl;
import org.glassfish.enterprise.concurrent.test.ClassloaderContextSetupProvider;
import org.glassfish.enterprise.concurrent.test.ManagedBlockingRunnableTask;
import org.glassfish.enterprise.concurrent.test.ManagedCallableTask;
import org.glassfish.enterprise.concurrent.test.ManagedRunnableTask;
import org.glassfish.enterprise.concurrent.test.ManagedTaskListenerImpl;
import org.glassfish.enterprise.concurrent.test.ManagedTimeRecordingRunnableTask;
import org.glassfish.enterprise.concurrent.test.RunnableImpl;
import org.glassfish.enterprise.concurrent.test.TestContextService;
import org.glassfish.enterprise.concurrent.test.TimeRecordingCallableImpl;
import org.glassfish.enterprise.concurrent.test.TimeRecordingRunnableImpl;
import org.glassfish.enterprise.concurrent.test.Util;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;


public class ManagedScheduledExecutorServiceAdapterTest extends ManagedExecutorServiceAdapterTest {
 
    /**
     * verifies that task got run when scheduled using schedule(Callable, delay, unit)
     */
    @Test
    public void testSchedule_Callable_delay() {
        final String classloaderName = "testSchedule_Callable_delay" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String result = "result" + new Date(System.currentTimeMillis());        
        CallableImpl<String> task = new CallableImpl<>(result, null);
        ManagedScheduledExecutorService instance = 
                createManagedScheduledExecutor("testSchedule_Callable_delay", contextCallback);
        ScheduledFuture<String> future = instance.schedule(task, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay < 1000);
        try {
            System.out.println("future.get() blocking to get result");
            assertEquals(result, future.get());
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            fail();
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());        
        task.verifyAfterRun(classloaderName); // verify context is setup for task
    }

    /**
     * verifies that task got run when scheduled using schedule(Callable, delay, unit, taskListener)
     */
    @Test
    public void testSchedule_Callable_delay_withListener() {
        final String classloaderName = "testSchedule_Callable_delay_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String result = "result" + new Date(System.currentTimeMillis());
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        CallableImpl<String> task = new ManagedCallableTask<>(result, taskListener);
        ManagedScheduledExecutorService instance = 
                createManagedScheduledExecutor("testSchedule_Callable_delay_withListener", contextCallback);
        ScheduledFuture<String> future = instance.schedule(task, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay < 1000);
        try {
            System.out.println("future.get() blocking to get result");
            assertEquals(result, future.get());
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            fail();
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());        
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, task, null, classloaderName);
    }

    /**
     * verifies that task got run when scheduled using schedule(Callable, delay, unit, taskListener)
     * with task throwing exception
     */
    @Test
    public void testSchedule_Callable_delay_exception_withListener() {
        final String classloaderName = "testSchedule_Callable_delay_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String result = "result" + new Date(System.currentTimeMillis());
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        CallableImpl<String> task = new ManagedCallableTask<>(result, taskListener);
        task.setThrowsException(true);
        ManagedScheduledExecutorService instance = 
                createManagedScheduledExecutor("testSchedule_Callable_delay_withListener", contextCallback);
        ScheduledFuture<String> future = instance.schedule(task, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay < 1000);
        ExecutionException exception = null;
        try {
            future.get();
            fail();
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            exception = ex;
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertNotNull(exception);
        assertEquals(result, exception.getCause().getMessage());
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, task, null, classloaderName);
    }


    /**
     * verifies that task run is delayed when scheduled using schedule(Callable, delay, unit)
     */
    @Test 
    public void testSchedule_Callable_long_delay() {
        final String classloaderName = "testSchedule_Callable_long_delay" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String result = "result" + new Date(System.currentTimeMillis());        
        CallableImpl<String> task = new CallableImpl<>(result, null);
        ManagedScheduledExecutorService instance = 
                createManagedScheduledExecutor("testSchedule_Callable_long_delay", contextCallback);
        ScheduledFuture<String> future = instance.schedule(task, 1L, TimeUnit.HOURS);
        long delay = future.getDelay(TimeUnit.SECONDS);
        assertTrue(delay > 0 && delay < 36000);
        try {
            System.out.println("future.get() blocking for 1 second to get result");
            future.get(1L, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            fail("Expected TimeoutException not caught");
        } catch (ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TimeoutException ex) {
            // expected
        }
        assertFalse(future.isDone());
        
        // cancel the scheduled task
        future.cancel(true);
    }

    /**
     * verifies that task got run when scheduled using schedule(Runnable, delay, unit)
     */
    @Test
    public void testSchedule_Runnable_delay() {
        final String classloaderName = "testSchedule_Runnable_delay" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        RunnableImpl task = new RunnableImpl(null);
        ManagedScheduledExecutorService instance = 
                createManagedScheduledExecutor("testSchedule_Runnable_delay", contextCallback);
        ScheduledFuture future = instance.schedule(task, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay < 1000);
        try {
            System.out.println("future.get() blocking to get result");
            assertNull(future.get());
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            fail();
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());        
        task.verifyAfterRun(classloaderName); // verify context is setup for task
    }
   
    /**
     * verifies that task got run when scheduled using schedule(Runnable, delay, unit, taskListener)
     */
    @Test
    public void testSchedule_Runnable_delay_withListener() {
        final String classloaderName = "testSchedule_Runnable_delay_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        RunnableImpl task = new ManagedRunnableTask(taskListener);
        ManagedScheduledExecutorService instance = 
                createManagedScheduledExecutor("testSchedule_Runnable_delay_withListener", contextCallback);
        ScheduledFuture future = instance.schedule(task, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay < 1000);
        try {
            assertNull(future.get());
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            fail();
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());        
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, task, null, classloaderName);
    }

    /**
     * verifies that task got run when scheduled using schedule(Runnable, delay, unit, taskListener)
     * with task throwing exception
     */
    @Test
    public void testSchedule_Runnable_delay_exception_withListener() {
        final String classloaderName = "testSchedule_Runnable_delay_exception_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final RuntimeException result = new RuntimeException("result" + new Date(System.currentTimeMillis()));
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        RunnableImpl task = new ManagedRunnableTask(taskListener, result);
        ManagedScheduledExecutorService instance = 
                createManagedScheduledExecutor("testSchedule_Runnable_delay_exception_withListener", contextCallback);
        ScheduledFuture future = instance.schedule(task, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay < 1000);
        ExecutionException exception = null;
        try {
            future.get();
            fail();
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            exception = ex;
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertNotNull(exception);
        assertEquals(result, exception.getCause());
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, task, null, classloaderName);
    }

    /**
     * verifies that task run is delayed when scheduled using schedule(Runnable, delay, unit)
     */
    @Test 
    public void testSchedule_Runnable_long_delay() {
        final String classloaderName = "testSchedule_Runnable_long_delay" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String result = "result" + new Date(System.currentTimeMillis());        
        RunnableImpl task = new RunnableImpl(null);
        ManagedScheduledExecutorService instance = 
                createManagedScheduledExecutor("testSchedule_Runnable_long_delay", contextCallback);
        ScheduledFuture future = instance.schedule(task, 1L, TimeUnit.HOURS);
        long delay = future.getDelay(TimeUnit.SECONDS);
        assertTrue(delay > 0 && delay < 36000);
        try {
            future.get(1L, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            fail("Expected TimeoutException not caught");
        } catch (ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TimeoutException ex) {
            // expected
        }
        assertFalse(future.isDone());
        
        // cancel the scheduled task
        future.cancel(true);
    }

    @Test
    public void testScheduleAtFixedRate()  {
        final String classloaderName = "testScheduleAtFixedRate" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        TimeRecordingRunnableImpl task = new TimeRecordingRunnableImpl(null);
        ManagedScheduledExecutorService instance = 
                createManagedScheduledExecutor("testScheduleAtFixedRate", contextCallback);
        ScheduledFuture future = instance.scheduleAtFixedRate(task, 2L, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay <= 2000);
        try {
            System.out.println("sleeping for 4 seconds");
            // wait 4 seconds, task should have triggered at 2s, 3s, 4s etc
            assertNull(future.get(4, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            fail();
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
            System.out.println("timeout: " + new Date(timeout).toString());
        }
        task.verifyAfterRun(classloaderName); // verify context is setup for task        
    }
    
    @Test
    public void testScheduleAtFixedRate_withListener()  {
        final String classloaderName = "testScheduleAtFixedRate_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        TimeRecordingRunnableImpl task = new ManagedTimeRecordingRunnableTask(taskListener);
        ManagedScheduledExecutorService instance = 
                createManagedScheduledExecutor("testScheduleAtFixedRate_withListener", contextCallback);
        ScheduledFuture future = instance.scheduleAtFixedRate(task, 2L, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay <= 2000);
        try {
            System.out.println("sleeping for 4 seconds");
            // wait 4 seconds, task should have triggered at 2s, 3s, 4s etc
            assertNull(future.get(4, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            fail();
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
            System.out.println("timeout: " + new Date(timeout).toString());
        }
        task.verifyAfterRun(classloaderName, false); // verify context is setup for task

        // task submitted event should be called only once
        assertEquals(1, taskListener.getCount(future, ManagedTaskListenerImpl.SUBMITTED));
        // task should be called at least 2 times, verify that listener events
        // also got called at least 2 times
        assertTrue(taskListener.getCount(future, ManagedTaskListenerImpl.STARTING) >= 2);
        assertTrue(taskListener.getCount(future, ManagedTaskListenerImpl.DONE) >= 2);
        
        taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, task, null, classloaderName);
    }
    
    @Test
    public void testScheduleWithFixedDelay()  {
        final String classloaderName = "testScheduleWithFixedDelay" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        TimeRecordingRunnableImpl task = new TimeRecordingRunnableImpl(null);
        ManagedScheduledExecutorService instance = 
                createManagedScheduledExecutor("testScheduleWithFixedDelay", contextCallback);
        ScheduledFuture future = instance.scheduleWithFixedDelay(task, 1L, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay <= 1000);
        try {
            System.out.println("sleeping for 4 seconds");
            // wait 4 seconds, task should have triggered at 1s, 3s, 5s etc since
            // each task took 1 second to run (due to the sleep() call)
            assertNull(future.get(4, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            fail();
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
            System.out.println("timeout: " + new Date(timeout).toString());
        }
        task.verifyAfterRun(classloaderName); // verify context is setup for task        
    }
    
    @Test
    public void testScheduleWithFixedDelay_withListener()  {
        final String classloaderName = "testScheduleWithFixedDelay_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        TimeRecordingRunnableImpl task = new ManagedTimeRecordingRunnableTask(taskListener);
        ManagedScheduledExecutorService instance = 
                createManagedScheduledExecutor("testScheduleWithFixedDelay_withListener", contextCallback);
        ScheduledFuture future = instance.scheduleWithFixedDelay(task, 1L, 1L, TimeUnit.SECONDS);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay <= 1000);
        try {
            System.out.println("sleeping for 4 seconds");
            // wait 4 seconds, task should have triggered at 1s, 3s, 5s etc since
            // each task took 1 second to run (due to the sleep() call)
            assertNull(future.get(4, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            fail();
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
            System.out.println("timeout: " + new Date(timeout).toString());
        }
        task.verifyAfterRun(classloaderName, false); // verify context is setup for task

        // task submitted event should be called only once
        assertEquals(1, taskListener.getCount(future, ManagedTaskListenerImpl.SUBMITTED));
        // task should be called at least 2 times, verify that listener events
        // also got called at least 2 times
        assertTrue(taskListener.getCount(future, ManagedTaskListenerImpl.STARTING) >= 2);
        assertTrue(taskListener.getCount(future, ManagedTaskListenerImpl.DONE) >= 2);
        
        taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, task, null, classloaderName);
    }
   
    /**
     * 
     */
    @Test
    public void testSchedule_Runnable_trigger() {
        final String classloaderName = "testSchedule_Runnable_trigger" + new Date(System.currentTimeMillis());
        boolean gotSkippedException = false;
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        TimeRecordingRunnableImpl task = new TimeRecordingRunnableImpl(taskListener); 
        ManagedScheduledExecutorService instance = 
                createManagedScheduledExecutor("testSchedule_Runnable_trigger", contextCallback);
        // This trigger will return values of now+1s, now+2s, now+3s for getNextRunTime()
        // and will return values of false, true, false for skipRun()
        TestTriggerImpl trigger = new TestTriggerImpl(1, -1, 1);
        ScheduledFuture future = instance.schedule(task, trigger);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay > 500 && delay < 1500);
        System.out.println("sleeping until done, approximately 3 seconds");
        while(!future.isDone()) {
            try {
                future.get(100, TimeUnit.MILLISECONDS);
            } catch (SkippedException se) {
                gotSkippedException = true;
            } catch (ExecutionException ee) {

            } catch (TimeoutException  | InterruptedException e) {

            }
        }
        assertTrue(gotSkippedException);
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());        
        task.verifyAfterRun(classloaderName, false); // verify context is setup for task
        try {
            assertNull(future.get());
        } catch (InterruptedException | ExecutionException ex) {
        }
        ArrayList<Long> timeouts = task.getInvocations();
        for (Long timeout: timeouts) {
            System.out.println("timeout: " + new Date(timeout).toString());
        }
        taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, task, null, classloaderName);
        taskListener.verifyCallback(ManagedTaskListenerImpl.DONE, future, instance, task, null, classloaderName);
        assertFalse(taskListener.eventCalled(future, ManagedTaskListenerImpl.ABORTED));
   }

    @Test
    public void testSchedule_Runnable_trigger_exception() {
        final String classloaderName = "testSchedule_Runnable_trigger_exception" + new Date(System.currentTimeMillis());
        boolean gotSkippedException = false;
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final RuntimeException result = new RuntimeException("result" + new Date(System.currentTimeMillis()));
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        TimeRecordingRunnableImpl task = new TimeRecordingRunnableImpl(taskListener, result); 
        ManagedScheduledExecutorService instance = 
                createManagedScheduledExecutor("testSchedule_Runnable_trigger_exception", contextCallback);
        // This trigger will return values of now+1s, now+2s, now+3s for getNextRunTime()
        // and will return values of false, true, false for skipRun()
        TestTriggerImpl trigger = new TestTriggerImpl(1, -1, 1);
        ScheduledFuture future = instance.schedule(task, trigger);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay > 500 && delay < 1500);
        System.out.println("sleeping until done, approximately 3 seconds");
        while(!future.isDone()) {
            try {
                future.get(100, TimeUnit.MILLISECONDS);
                fail("expected exception not thrown");
            } catch (SkippedException se) {
                gotSkippedException = true;
            } catch (ExecutionException ee) {

            } catch (TimeoutException  | InterruptedException e) {

            }
        }
        assertTrue(gotSkippedException);
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());        
        task.verifyAfterRun(classloaderName, false); // verify context is setup for task
        Exception exception = null;
        try {
            future.get();
            fail("expected exception not thrown");
        } catch (InterruptedException ex) {
        } catch (ExecutionException ex) {
            exception = ex;
        }
        assertNotNull(exception);
        assertTrue(result == exception.getCause());
        ArrayList<Long> timeouts = task.getInvocations();
        for (Long timeout: timeouts) {
            System.out.println("timeout: " + new Date(timeout).toString());
        }
        taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, task, null, classloaderName);
        taskListener.verifyCallback(ManagedTaskListenerImpl.DONE, future, instance, task, null, classloaderName);
        assertFalse(taskListener.eventCalled(future, ManagedTaskListenerImpl.ABORTED));
    }
   
    @Test
    public void testSchedule_Callable_trigger() {
        final String classloaderName = "testSchedule_Callable_trigger" + new Date(System.currentTimeMillis());
        boolean gotSkippedException = false;
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String result = "result" + new Date(System.currentTimeMillis());        
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        TimeRecordingCallableImpl<String> task = new TimeRecordingCallableImpl<>(result, taskListener);
        ManagedScheduledExecutorService instance = 
                createManagedScheduledExecutor("testSchedule_Callable_trigger", contextCallback);
        // This trigger will return values of now+1s, now+2s, now+3s for getNextRunTime()
        // and will return values of false, true, false for skipRun()
        TestTriggerImpl trigger = new TestTriggerImpl(1, -1, 1);
        ScheduledFuture future = instance.schedule(task, trigger);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay > 500 && delay < 1500);
        System.out.println("sleeping until done, approximately 3 seconds");
        while(!future.isDone()) {
            try {
                future.get(100, TimeUnit.MILLISECONDS);
            } catch (SkippedException se) {
                gotSkippedException = true;
            } catch (ExecutionException ee) {

            } catch (TimeoutException  | InterruptedException e) {

            }
        }
        assertTrue(gotSkippedException);
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());        
        task.verifyAfterRun(classloaderName, false); // verify context is setup for task
        try {
            assertEquals(result, future.get());
        } catch (InterruptedException | ExecutionException ex) {
            
        }
        ArrayList<Long> timeouts = task.getInvocations();
        for (Long timeout: timeouts) {
            System.out.println("timeout: " + new Date(timeout).toString());
        }
        taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, task, null, classloaderName);
        taskListener.verifyCallback(ManagedTaskListenerImpl.DONE, future, instance, task, null, classloaderName);
        assertFalse(taskListener.eventCalled(future, ManagedTaskListenerImpl.ABORTED));
    }
   
    @Test
    public void testSchedule_Callable_trigger_exception() {
        final String classloaderName = "testSchedule_Callable_trigger_exception" + new Date(System.currentTimeMillis());
        boolean gotSkippedException = false;
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String result = "result" + new Date(System.currentTimeMillis());        
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        TimeRecordingCallableImpl<String> task = new TimeRecordingCallableImpl<>(result, taskListener);
        task.setThrowsException(true);
        ManagedScheduledExecutorService instance = 
                createManagedScheduledExecutor("testSchedule_Callable_trigger_exception", contextCallback);
        // This trigger will return values of now+1s, now+2s, now+3s for getNextRunTime()
        // and will return values of false, true, false for skipRun()
        TestTriggerImpl trigger = new TestTriggerImpl(1, -1, 1);
        ScheduledFuture future = instance.schedule(task, trigger);
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay > 500 && delay < 1500);
        System.out.println("sleeping until done, approximately 3 seconds");
        while(!future.isDone()) {
            try {
                future.get(100, TimeUnit.MILLISECONDS);
                fail("expected exception not thrown");
            } catch (SkippedException se) {
                gotSkippedException = true;
            } catch (ExecutionException ee) {

            } catch (TimeoutException  | InterruptedException e) {

            }
        }
        assertTrue(gotSkippedException);
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());        
        task.verifyAfterRun(classloaderName, false); // verify context is setup for task
        Exception exception = null;
        try {
            future.get();
            fail("expected exception not thrown");
        } catch (InterruptedException ex) {
        } catch (ExecutionException ex) {
            exception = ex;
        }
        assertNotNull(exception);
        assertEquals(result, exception.getCause().getMessage());
        ArrayList<Long> timeouts = task.getInvocations();
        for (Long timeout: timeouts) {
            System.out.println("timeout: " + new Date(timeout).toString());
        }
        taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, task, null);
        taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, task, null, classloaderName);
        taskListener.verifyCallback(ManagedTaskListenerImpl.DONE, future, instance, task, null, classloaderName);
        assertFalse(taskListener.eventCalled(future, ManagedTaskListenerImpl.ABORTED));
    }
   
    @Test
    public void testSchedule_trigger_cancel() throws InterruptedException {
        final String classloaderName = "testSchedule_trigger_cancel" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        ManagedBlockingRunnableTask task = new ManagedBlockingRunnableTask(taskListener, 60 * 1000L);
        ManagedScheduledExecutorService instance = 
                createManagedScheduledExecutor("testSchedule_trigger_exception", contextCallback);
        TestTriggerImpl trigger = new TestTriggerImpl(1, -1, 1);
        ScheduledFuture future = instance.schedule(task, trigger);
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        
        assertTrue( future.cancel(true) );
        
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());        

        assertTrue("timeout waiting for taskAborted call", Util.waitForTaskAborted(future, taskListener, getLoggerName()));
        taskListener.verifyCallback(ManagedTaskListenerImpl.ABORTED, future, instance, 
                task, new CancellationException());
        
        // test also that task only executed once as it should have been cancelled. 
        Thread.sleep(2000);
        assertTrue("Task executions should be 1 as trigger task was cancelled ", taskListener.getCount(future, ManagedTaskListenerImpl.STARTING) == 1);
        
        
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
        Date scheduledStart, runStart, runEnd;
        
        public LastExecutionCopy(LastExecution source) {
            if (source != null) {
                identityName = source.getIdentityName();
                result = source.getResult();
                scheduledStart = source.getScheduledStart();
                runStart = source.getRunStart();
                runEnd = source.getRunEnd();
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
        public Date getScheduledStart() {
            return scheduledStart;
        }

        @Override
        public Date getRunStart() {
            return runStart;
        }

        @Override
        public Date getRunEnd() {
            return runEnd;
        }
        
        @Override
        public String toString() {
            return "[LastExecutionInfo] identityName: " + identityName + ", result: " + result + ", scheduledStart: " + scheduledStart + 
                    ", runStart: " + runStart + ", runEnd: " + runEnd;
        }
        
    }
}
