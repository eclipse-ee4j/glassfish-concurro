/*
 * Copyright (c) 2023, 2024 Contributors to the Eclipse Foundation.
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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import org.glassfish.concurro.AbstractManagedExecutorService;
import org.glassfish.concurro.ManagedExecutorServiceImpl;
import org.glassfish.concurro.AbstractManagedExecutorService.RejectPolicy;
import org.glassfish.concurro.spi.ContextSetupProvider;
import org.glassfish.concurro.test.BlockingRunnableImpl;
import org.glassfish.concurro.test.ManagedBlockingRunnableTask;
import org.glassfish.concurro.test.ManagedTaskListenerImpl;
import org.glassfish.concurro.test.RunnableImpl;
import org.glassfish.concurro.test.TestContextService;
import org.glassfish.concurro.test.Util;
import org.glassfish.concurro.test.Util.BooleanValueProducer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Tests for Life cycle APIs in ManagedExecutorServiceImpl
 *
 */
public class ManagedExecutorServiceImplTest {

    /**
     * Test for shutdown and isShutdown to verify that we do not regress Java SE
     * ExecutorService functionality
     */
    @Test
    public void testShutdown() {
        ManagedExecutorService mes =
                createManagedExecutor("testShutdown", null);
        assertFalse(mes.isShutdown());
        mes.shutdown();
        assertTrue(mes.isShutdown());
    }

    /**
     * Verifies that when the executor is shut down using shutdownNow()
     * - all submitted tasks are cancelled if not running, and
     * - all running task threads are interrupted, and
     * - all registered ManagedTaskListeners are invoked
     *
     **/
    @Test
    public void testShutdownNow_tasks_behavior() {
        ManagedExecutorService mes =
                createManagedExecutor("testShutdown_tasks_behavior", 1, 2, 2); // core=1, queue=2
        ManagedTaskListenerImpl listener1 = new ManagedTaskListenerImpl();
        final BlockingRunnableImpl task1 = new ManagedBlockingRunnableTask(listener1, 0L);
        Future f1 = mes.submit(task1); // this task should be run

        ManagedTaskListenerImpl listener2 = new ManagedTaskListenerImpl();
        BlockingRunnableImpl task2 = new ManagedBlockingRunnableTask(listener2, 0L);
        Future f2 = mes.submit(task2); // this task should be queued

        ManagedTaskListenerImpl listener3 = new ManagedTaskListenerImpl();
        BlockingRunnableImpl task3 = new ManagedBlockingRunnableTask(listener3, 0L);
        Future f3 = mes.submit(task3); // this task should be queued
        // waits for task1 to start
        Util.waitForTaskStarted(f1, listener1, getLoggerName());

        mes.shutdownNow();

        // task2 and task3 should be cancelled
        Util.waitForTaskAborted(f2, listener2, getLoggerName());
        assertTrue(f2.isCancelled());
        assertTrue(listener2.eventCalled(f2, ManagedTaskListenerImpl.ABORTED));

        Util.waitForTaskAborted(f3, listener3, getLoggerName());
        assertTrue(f3.isCancelled());
        assertTrue(listener3.eventCalled(f3, ManagedTaskListenerImpl.ABORTED));

        // task1 should be interrupted
        Util.waitForBoolean(
            new Util.BooleanValueProducer() {
              public boolean getValue() {
                return task1.isInterrupted();
              }
            }, true, getLoggerName());
        assertTrue(task1.isInterrupted());
    }


    /**
     * Test for shutdownNow to verify that we do not regress Java SE
     * ExecutorService functionality
     */
    @Test
    public void testShutdownNow() {
        ManagedExecutorService mes =
                createManagedExecutor("testShutdownNow", null);
        assertFalse(mes.isShutdown());
        List<Runnable> tasks = mes.shutdownNow();
        assertTrue(tasks.isEmpty());
        assertTrue(mes.isShutdown());
        assertTrue(mes.isTerminated());
    }

    /**
     * Test for shutdownNow with unfinished task
     * to verify that we do not regress Java SE
     * ExecutorService functionality
     */
    @Test
    public void testShutdownNow_unfinishedTask() {
        ManagedExecutorService mes =
                createManagedExecutor("testShutdown_unfinishedTask", null);
        assertFalse(mes.isShutdown());
        ManagedTaskListenerImpl listener = new ManagedTaskListenerImpl();
        BlockingRunnableImpl task1 = new ManagedBlockingRunnableTask(listener, 0L);
        Future f = mes.submit(task1);
        // waits for task to start
        Util.waitForTaskStarted(f, listener, getLoggerName());
        RunnableImpl task2 = new RunnableImpl(null);
        mes.submit(task2); // this task cannot start until task1 has finished
        List<Runnable> tasks = mes.shutdownNow();
        assertFalse(mes.isTerminated());

        assertTrue(tasks.size() > 0);
        task1.stopBlocking();
        assertTrue(mes.isShutdown());
    }

    /**
     * Test for awaitsTermination with unfinished task
     * to verify that we do not regress Java SE
     * ExecutorService functionality
     */
    @Test
    public void testAwaitsTermination() throws Exception {
        ManagedExecutorService mes =
                createManagedExecutor("testAwaitsTermination", null);
        assertFalse(mes.isShutdown());
        ManagedTaskListenerImpl listener = new ManagedTaskListenerImpl();
        BlockingRunnableImpl task = new ManagedBlockingRunnableTask(listener, 0L);
        Future f = mes.submit(task);
        // waits for task to start
        Util.waitForTaskStarted(f, listener, getLoggerName());
        mes.shutdown();
        try {
            assertFalse(mes.awaitTermination(1, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceImplTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        task.stopBlocking();
        try {
            assertTrue(mes.awaitTermination(10, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceImplTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assertTrue(mes.isTerminated());
    }

    @Test
    public void testQueueCreation() throws Exception {
        // Case 1: corePoolSize of 0, queue size of Integer.MAX_VALUE
        //         A SynchronousQueue should be created
        ManagedExecutorServiceImpl mes1 = createManagedExecutor("mes1", 0, 1, Integer.MAX_VALUE);
        BlockingQueue<Runnable> queue = getQueue(mes1);
        assertTrue(queue instanceof SynchronousQueue);
        assertEquals(0, queue.remainingCapacity());

        // Case 2: corePoolSize of non-zero, queue size of Integer.MAX_VALUE
        //         An unbounded queue should be created
        ManagedExecutorServiceImpl mes2 = createManagedExecutor("mes2", 1, 1, Integer.MAX_VALUE);
        queue = getQueue(mes2);
        assertEquals(Integer.MAX_VALUE, queue.remainingCapacity());

        // Case 3: queue size of 0, A SynchronousQueue should be created
        ManagedExecutorServiceImpl mes3 = createManagedExecutor("mes3", 0, 1, 0);
        queue = getQueue(mes3);
        assertTrue(queue instanceof SynchronousQueue);
        assertEquals(0, queue.remainingCapacity());

        // Case 4: queue size not 0 or Integer.MAX_VALUE,
        //         A queue with specified capacity should be created
        final int QUEUE_SIZE = 1234;
        ManagedExecutorServiceImpl mes4 = createManagedExecutor("mes4", 0, 1, QUEUE_SIZE);
        queue = getQueue(mes4);
        assertEquals(QUEUE_SIZE, queue.remainingCapacity());

        ManagedExecutorServiceImpl mes5 = createManagedExecutor("mes5", 1, 10, QUEUE_SIZE);
        queue = getQueue(mes5);
        assertEquals(QUEUE_SIZE, queue.remainingCapacity());
    }

    @Test
    public void testConstructorWithGivenQueue() {
        final int QUEUE_SIZE = 8765;
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(QUEUE_SIZE);
        ManagedExecutorServiceImpl mes =
                new ManagedExecutorServiceImpl("mes", null, 0, false,
                1, 10, 0, TimeUnit.SECONDS, 0L,
                new TestContextService(null), RejectPolicy.ABORT,
                queue);
        assertEquals(queue, getQueue(mes));
        assertEquals(QUEUE_SIZE, getQueue(mes).remainingCapacity());
    }

    @Test
    public void testTaskCounters() {
        final AbstractManagedExecutorService mes =
                (AbstractManagedExecutorService) createManagedExecutor("testTaskCounters", null);
        assertEquals(0, mes.getTaskCount());
        assertEquals(0, mes.getCompletedTaskCount());
        RunnableImpl task = new RunnableImpl(null);
        Future future = mes.submit(task);
        try {
            future.get();
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assertTrue(future.isDone());
        Util.waitForBoolean(new BooleanValueProducer() {
            @Override
            public boolean getValue() {
                return (mes.getTaskCount() > 0) && (mes.getCompletedTaskCount() > 0);
            }
          }
                , true, getLoggerName()
        );

        assertEquals(1, mes.getTaskCount());
        assertEquals(1, mes.getCompletedTaskCount());
    }

    @Test
    public void testThreadLifeTime() {
        final AbstractManagedExecutorService mes =
                createManagedExecutor("testThreadLifeTime",
                1, 2, 0, 3L, 0L, false);

        Collection<Thread> threads = mes.getThreads();
        assertNull(threads);

        RunnableImpl runnable = new RunnableImpl(null);
        Future f = mes.submit(runnable);
        try {
            f.get();
        } catch (Exception ex) {
            Logger.getLogger(ManagedExecutorServiceImplTest.class.getName()).log(Level.SEVERE, null, ex);
        }

        threads = mes.getThreads();
        assertEquals(1, threads.size());
        System.out.println("Waiting for threads to expire due to threadLifeTime");
        Util.waitForBoolean(new BooleanValueProducer() {
            public boolean getValue() {
                // wait for all threads get expired
                return mes.getThreads() == null;
            }
        }, true, getLoggerName());

    }

    @Test
    public void testHungThreads() {
        final AbstractManagedExecutorService mes =
                createManagedExecutor("testThreadLifeTime",
                1, 2, 0, 0L, 1L, false);

        Collection<Thread> threads = mes.getHungThreads();
        assertNull(threads);

        BlockingRunnableImpl runnable = new BlockingRunnableImpl(null, 0L);
        Future f = mes.submit(runnable);
        try {
            Thread.sleep(1000); // sleep for 1 second
        } catch (InterruptedException ex) {
        }

        // should get one hung thread
        threads = mes.getHungThreads();
        assertEquals(1, threads.size());

        // tell task to stop waiting
        runnable.stopBlocking();
        Util.waitForTaskComplete(runnable, getLoggerName());

        // should not have any more hung threads
        threads = mes.getHungThreads();
        assertNull(threads);
    }

    @Test
    public void testHungThreads_LongRunningTasks() {
        final AbstractManagedExecutorService mes =
                createManagedExecutor("testThreadLifeTime",
                1, 2, 0, 0L, 1L, true);

        Collection<Thread> threads = mes.getHungThreads();
        assertNull(threads);

        BlockingRunnableImpl runnable = new BlockingRunnableImpl(null, 0L);
        Future f = mes.submit(runnable);
        try {
            Thread.sleep(1000); // sleep for 1 second
        } catch (InterruptedException ex) {
        }

        // should not get any hung thread because longRunningTasks is true
        threads = mes.getHungThreads();
        assertNull(threads);

        // tell task to stop waiting
        runnable.stopBlocking();
        Util.waitForTaskComplete(runnable, getLoggerName());

        // should not have any more hung threads
        threads = mes.getHungThreads();
        assertNull(threads);
    }

    protected ManagedExecutorService createManagedExecutor(String name,
            ContextSetupProvider contextCallback) {
        return new ManagedExecutorServiceImpl(name, null, 0, false,
                1, 1,
                0, TimeUnit.SECONDS,
                0L,
                Integer.MAX_VALUE,
                new TestContextService(contextCallback),
                RejectPolicy.ABORT);
    }

    protected ManagedExecutorServiceImpl createManagedExecutor(String name,
        int corePoolSize, int maxPoolSize, int queueSize) {
        return createManagedExecutor(name, corePoolSize, maxPoolSize,
                queueSize, 0L, 0L, false);
    }

    protected ManagedExecutorServiceImpl createManagedExecutor(String name,
            int corePoolSize, int maxPoolSize, int queueSize, long threadLifeTime,
            long hungTask, boolean longRunningTasks) {
        return new ManagedExecutorServiceImpl(name, null, hungTask,
                longRunningTasks,
                corePoolSize, maxPoolSize,
                0, TimeUnit.SECONDS,
                threadLifeTime,
                queueSize,
                new TestContextService(null),
                RejectPolicy.ABORT);
    }

    BlockingQueue<Runnable> getQueue(ManagedExecutorServiceImpl mes) {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) mes.getThreadPoolExecutor();
        return executor.getQueue();
    }

    public String getLoggerName() {
        return ManagedExecutorServiceImplTest.class.getName();
    }

}
