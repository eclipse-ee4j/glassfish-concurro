/*
 * Copyright (c) 2023, 2025 Contributors to the Eclipse Foundation.
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
package org.glassfish.concurro.test.virtualthreads;

import jakarta.enterprise.concurrent.ManagedExecutorService;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.glassfish.concurro.AbstractManagedExecutorService;
import org.glassfish.concurro.ContextServiceImpl;
import org.glassfish.concurro.spi.ContextSetupProvider;
import org.glassfish.concurro.test.AwaitableManagedTestTask;
import org.glassfish.concurro.test.BlockingRunnableForTest;
import org.glassfish.concurro.test.FakeRunnableForTest;
import org.glassfish.concurro.test.ManagedBlockingRunnableTask;
import org.glassfish.concurro.test.ManagedRunnableTestTask;
import org.glassfish.concurro.test.ManagedTestTaskListener;
import org.glassfish.concurro.test.TestContextService;
import org.glassfish.concurro.test.Util;
import org.glassfish.concurro.virtualthreads.VirtualThreadsManagedExecutorService;
import org.glassfish.concurro.virtualthreads.VirtualThreadsManagedThreadFactory;
import org.hamcrest.collection.IsEmptyCollection;
import org.junit.Test;

import static org.glassfish.concurro.AbstractManagedExecutorService.RejectPolicy.ABORT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests for Life cycle APIs in VirtualThreadsManagedExecutorService
 *
 */
public class VirtualThreadsManagedExecutorServiceIT {

    System.Logger logger = System.getLogger(this.getClass().getName());

    /**
     * Test for shutdown and isShutdown to verify that we do not regress Java SE ExecutorService functionality
     */
    @Test
    public void testShutdown() {
        ManagedExecutorService managedExecutorService = createManagedExecutorWithMaxOneParallelTask("testShutdown", null);
        assertFalse(managedExecutorService.isShutdown());

        managedExecutorService.shutdown();
        assertTrue(managedExecutorService.isShutdown());
    }

    /**
     * Verifies that when the executor is shut down using shutdownNow() - all submitted tasks are cancelled if not running,
     * and - all running task threads are interrupted, and - all registered ManagedTaskListeners are invoked
     *
     *
     */
    @Test
    public void testShutdownNow_tasks_behavior() throws InterruptedException, InterruptedException {
        ManagedExecutorService managedExecutorService = createManagedExecutor("testShutdown_tasks_behavior", 2, 2); // max tasks=2, queue=2
        TestableExecution<ManagedBlockingRunnableTask> execution1 = new TestableExecution<>("task1", TestableExecution::busyWaitingTask);
        logger.log(System.Logger.Level.INFO, execution1.name + ": " + execution1.task);
        execution1.submitTo(managedExecutorService); // this task should be run

        TestableExecution<ManagedBlockingRunnableTask> execution2 = new TestableExecution<>("task2", TestableExecution::busyWaitingTask);
        execution2.submitTo(managedExecutorService); // this task should be queued

        TestableExecution<ManagedBlockingRunnableTask> execution3 = new TestableExecution<>("task3", TestableExecution::busyWaitingTask);
        execution3.submitTo(managedExecutorService); // this task should be queued

        // waits for task1 to start
        execution1.assertTaskStarted();

        managedExecutorService.shutdownNow();

        // task2 and task3 should be cancelled
        execution2.assertTaskAborted();
        execution3.assertTaskAborted();

        // task1 should be interrupted
        Util.waitForBoolean(execution1.task::isInterrupted, true, getLoggerName());
        assertTrue(execution1.task.isInterrupted());
    }

    @Test
    public void testMaxParallelTasks_limitation() throws InterruptedException {
        ManagedExecutorService mes = createManagedExecutor("testShutdown_tasks_behavior", 2, 2); // max tasks=2, queue=2
        TestableExecution<ManagedBlockingRunnableTask> execution1 = new TestableExecution<>("task1",
                exec -> new ManagedBlockingRunnableTask(exec.listener, 0));
        execution1.submitTo(mes); // this task should be run

        TestableExecution<ManagedBlockingRunnableTask> execution2 = new TestableExecution<>("task2",
                exec -> new ManagedBlockingRunnableTask(exec.listener, 0));
        execution2.submitTo(mes); // this task should be queued

        // waits for task1 & task2 to start
        execution1.assertTaskStarted();
        execution2.assertTaskStarted();

        TestableExecution<ManagedRunnableTestTask> execution3 = new TestableExecution<>("task3",
            exec -> new ManagedRunnableTestTask(exec.listener));
        execution3.submitTo(mes); // this task should be queued

        // wait for some time so tasks have some chance to start before assertions are made
        Thread.sleep(Duration.ofSeconds(1));

        // task3 should wait with starting while the other 2 tasks are running
        assertFalse(execution3.listener.eventCalled(execution3.future, ManagedTestTaskListener.STARTING));
        assertFalse(execution1.future.isDone());
        assertFalse(execution2.future.isDone());

        execution1.task.stopBlocking();
        execution2.task.stopBlocking();

        execution1.assertTaskCompleted();
        execution2.assertTaskCompleted();
        execution3.assertTaskCompleted();

        assertTrue(execution1.future.isDone());
        assertTrue(execution2.future.isDone());
        assertTrue(execution3.future.isDone());
    }

    @Test
    public void testMaxQueueSize_limitation() throws InterruptedException {
        // Max tasks=2, queue=2
        ManagedExecutorService managedExecutorService = createManagedExecutor("testMaxQueueSize_limitation", 2, 2);
        TestableExecution<ManagedBlockingRunnableTask> execution1 =
            new TestableExecution<>("task1", exec -> new ManagedBlockingRunnableTask(exec.listener, 0));

        // This task should be run
        execution1.submitTo(managedExecutorService);

        TestableExecution<ManagedBlockingRunnableTask> execution2 =
            new TestableExecution<>("task2", exec -> new ManagedBlockingRunnableTask(exec.listener, 0));

        // This task should be queued
        execution2.submitTo(managedExecutorService);

        // Waits for task1 & task2 to start
        execution1.assertTaskStarted();
        execution2.assertTaskStarted();

        // this task should be queued
        TestableExecution<ManagedRunnableTestTask> execution3
                = new TestableExecution<>("task3", exec -> new ManagedRunnableTestTask(exec.listener));
        execution3.submitTo(managedExecutorService);

        // This task should be queued
        TestableExecution<ManagedRunnableTestTask> execution4
                = new TestableExecution<>("task4", exec -> new ManagedRunnableTestTask(exec.listener));
        execution4.submitTo(managedExecutorService);

        // Wait for some time so tasks have some chance to start before assertions are made
        Thread.sleep(Duration.ofSeconds(1));

        // Task3 and task4 should wait with starting while the other 2 tasks are running
        assertFalse(execution3.listener.eventCalled(execution3.future, ManagedTestTaskListener.STARTING));
        assertFalse(execution4.listener.eventCalled(execution4.future, ManagedTestTaskListener.STARTING));
        assertFalse(execution1.future.isDone());
        assertFalse(execution2.future.isDone());

        TestableExecution<ManagedRunnableTestTask> execution5 =
            new TestableExecution<>("task5", exec -> new ManagedRunnableTestTask(exec.listener));

        // This task should be rejected because the queue is empty
        RejectedExecutionException ex = assertThrows(RejectedExecutionException.class, () -> {
            execution5.submitTo(managedExecutorService);
        });

        execution1.task.stopBlocking();
        execution2.task.stopBlocking();

        execution1.assertTaskCompleted();
        execution2.assertTaskCompleted();
        execution3.assertTaskCompleted();

        assertTrue(execution1.future.isDone());
        assertTrue(execution2.future.isDone());
        assertTrue(execution3.future.isDone());
    }

    /**
     * Test for shutdownNow to verify that we do not regress Java SE ExecutorService functionality
     */
    @Test
    public void testShutdownNow() {
        ManagedExecutorService managedExecutorService = createManagedExecutorWithMaxOneParallelTask("testShutdownNow", null);
        assertFalse(managedExecutorService.isShutdown());

        List<Runnable> tasks = managedExecutorService.shutdownNow();
        assertTrue(tasks.isEmpty());
        assertTrue(managedExecutorService.isShutdown());
        assertTrue(managedExecutorService.isTerminated());
    }

    /**
     * Test for shutdownNow with unfinished task to verify that we do not regress Java SE ExecutorService functionality
     */
    @Test
    public void testShutdownNow_unfinishedTask() {
        ManagedExecutorService managedExecutorService = createManagedExecutorWithMaxOneParallelTask("testShutdown_unfinishedTask", null);
        assertFalse(managedExecutorService.isShutdown());

        TestableExecution<ManagedBlockingRunnableTask> execution1 = new TestableExecution<>("task1", TestableExecution::busyWaitingTask);
        logger.log(System.Logger.Level.DEBUG, "Submitting " + execution1.name + " = " + execution1.task);
        execution1.submitTo(managedExecutorService);

        // Waits for task to start
        execution1.assertTaskStarted();

        FakeRunnableForTest task2 = new FakeRunnableForTest(null);
        // this task cannot start until task1 has finished
        managedExecutorService.submit(task2);
        List<Runnable> tasks = managedExecutorService.shutdownNow();
        assertFalse(managedExecutorService.isTerminated());

        assertThat(tasks.size(), is(greaterThan(0)));
        execution1.task.stopBlocking();
        assertTrue(managedExecutorService.isShutdown());
    }

    /**
     * Test for awaitsTermination with unfinished task to verify that we do not regress Java SE ExecutorService
     * functionality
     */
    @Test
    public void testAwaitsTermination() throws Exception {
        ManagedExecutorService managedExecutorService = createManagedExecutorWithMaxOneParallelTask("testAwaitsTermination", null);
        assertFalse(managedExecutorService.isShutdown());
        TestableExecution<ManagedBlockingRunnableTask> execution1 = new TestableExecution<>("task1", TestableExecution::busyWaitingTask);
        execution1.submitTo(managedExecutorService);

        // waits for task to start
        execution1.assertTaskStarted();
        managedExecutorService.shutdown();
        assertFalse(managedExecutorService.awaitTermination(1, TimeUnit.SECONDS));
        execution1.task.stopBlocking();
        assertTrue(managedExecutorService.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(managedExecutorService.isTerminated());
    }

    @Test
    public void testTaskCounters() throws Exception {
        final AbstractManagedExecutorService managedExecutorService
                = createManagedExecutorWithMaxOneParallelTask("testTaskCounters", null);
        assertEquals(0, managedExecutorService.getTaskCount());
        assertEquals(0, managedExecutorService.getCompletedTaskCount());
        FakeRunnableForTest task = new FakeRunnableForTest(null);
        Future future = managedExecutorService.submit(task);
        future.get();
        assertTrue(future.isDone());
        Util.waitForBoolean(
            () -> managedExecutorService.getTaskCount() > 0 && managedExecutorService.getCompletedTaskCount() > 0, true,
            getLoggerName());

        assertEquals(1, managedExecutorService.getTaskCount());
        assertEquals(1, managedExecutorService.getCompletedTaskCount());
    }

    @Test
    public void testThreadLifeTime() throws InterruptedException, ExecutionException, TimeoutException {
        final AbstractManagedExecutorService managedExecutorService =
            createManagedExecutor("testThreadLifeTime", 2, 0, 3L, 0L, false);

        Collection<Thread> threads = managedExecutorService.getThreads();
        assertTrue(threads.isEmpty());

        AwaitableManagedTestTask taskListener = new AwaitableManagedTestTask();
        ManagedRunnableTestTask runnable = new ManagedRunnableTestTask(taskListener);
        Future<?> future = managedExecutorService.submit(runnable);
        future.get(5, TimeUnit.SECONDS);
        assertEquals("I am ok!", taskListener.whenDone().get(5, TimeUnit.SECONDS));
        assertThat("All virtual threads should be discarded after tasks are done", managedExecutorService.getThreads(),
            IsEmptyCollection.empty());
    }

    @Test
    public void testHungThreads() throws Exception {
        final AbstractManagedExecutorService managedExecutorService = createManagedExecutor("testThreadLifeTime", 2, 0, 0L, 1L, false);

        Collection<Thread> threads = managedExecutorService.getHungThreads();
        assertTrue(threads.isEmpty());

        BlockingRunnableForTest runnable = new BlockingRunnableForTest(null, 0L);
        Future f = managedExecutorService.submit(runnable);
        Thread.sleep(1000); // sleep for 1 second

        // Should get one hung thread
        threads = managedExecutorService.getHungThreads();
        assertEquals(1, threads.size());

        // Tell task to stop waiting
        runnable.stopBlocking();
        Util.waitForTaskComplete(runnable, getLoggerName());

        // Should not have any more hung threads
        threads = managedExecutorService.getHungThreads();
        assertTrue(threads.isEmpty());
    }

    @Test
    public void testHungThreads_LongRunningTasks() throws Exception {
        final AbstractManagedExecutorService managedExecutorService = createManagedExecutor("testThreadLifeTime", 2, 0, 0L, 1L, true);

        Collection<Thread> threads = managedExecutorService.getHungThreads();
        assertTrue(threads.isEmpty());

        BlockingRunnableForTest runnable = new BlockingRunnableForTest(null, 0L);
        Future<?> future = managedExecutorService.submit(runnable);
        Thread.sleep(1000); // sleep for 1 second

        // Should not get any hung thread because longRunningTasks is true
        threads = managedExecutorService.getHungThreads();
        assertTrue(threads.isEmpty());

        // Tell task to stop waiting
        runnable.stopBlocking();
        Util.waitForTaskComplete(runnable, getLoggerName());

        // Should not have any more hung threads
        threads = managedExecutorService.getHungThreads();
        assertTrue(threads.isEmpty());
    }

    protected VirtualThreadsManagedExecutorServiceExt createManagedExecutorWithMaxOneParallelTask(String name, ContextSetupProvider contextCallback) {
        return new VirtualThreadsManagedExecutorServiceExt(name, new VirtualThreadsManagedThreadFactory(name), 0, false, 1,
                Integer.MAX_VALUE, new TestContextService(contextCallback), ABORT);
    }

    protected VirtualThreadsManagedExecutorServiceExt createManagedExecutor(String name, int maxParallelTasks, int queueSize) {
        return createManagedExecutor(name, maxParallelTasks, queueSize, 0L, 0L, false);
    }

    protected VirtualThreadsManagedExecutorServiceExt createManagedExecutor(String name, int maxParallelTasks, int queueSize, long threadLifeTime, long hungTask, boolean longRunningTasks) {
        return new VirtualThreadsManagedExecutorServiceExt(name, new VirtualThreadsManagedThreadFactory(name), hungTask, longRunningTasks,
                maxParallelTasks, queueSize, new TestContextService(null), ABORT);
    }

    public String getLoggerName() {
        return VirtualThreadsManagedExecutorServiceIT.class.getName();
    }

    public static class VirtualThreadsManagedExecutorServiceExt extends VirtualThreadsManagedExecutorService {

        public VirtualThreadsManagedExecutorServiceExt(String name, VirtualThreadsManagedThreadFactory managedThreadFactory,
                long hungTaskThreshold, boolean longRunningTasks, int maxParallelTasks, int queueCapacity,
                ContextServiceImpl contextService, RejectPolicy rejectPolicy) {
            super(name, managedThreadFactory, hungTaskThreshold, longRunningTasks, maxParallelTasks, queueCapacity, contextService,
                    rejectPolicy);
        }

        @Override
        public ExecutorService getThreadPoolExecutor() {
            return super.getThreadPoolExecutor();
        }

    }

    private class TestableExecution<RUNNABLE extends FakeRunnableForTest> {

        RUNNABLE task;
        Future future;
        ManagedTestTaskListener listener;
        String name;

        public TestableExecution(String name, Function<TestableExecution, RUNNABLE> taskCreator) {
            this.name = name;
            listener = new ManagedTestTaskListener();
            task = taskCreator.apply(this);
        }

        public void submitTo(ManagedExecutorService mes) {
            future = mes.submit(task);
        }

        public void assertTaskCompleted() {
            // tasks should complete successfully
            assertTrue(Util.waitForTaskComplete(task, getLoggerName()));
        }

        public void assertTaskStarted() {
            // waits for task1 to start
            assertTrue(Util.waitForTaskStarted(future, listener, getLoggerName()));
        }

        public void assertTaskAborted() {
            assertTrue(Util.waitForTaskAborted(future, listener, getLoggerName()));
            assertTrue(future.isCancelled());
            assertTrue(listener.eventCalled(future, ManagedTestTaskListener.ABORTED));
        }

        public static ManagedBlockingRunnableTask busyWaitingTask(TestableExecution exec) {
            return new ManagedBlockingRunnableTask(exec.listener, 0);
        }

    }

}
