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

import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;

import org.glassfish.concurro.ContextServiceImpl;
import org.glassfish.concurro.spi.ContextSetupProvider;
import org.glassfish.concurro.test.ClassloaderContextSetupProvider;
import org.glassfish.concurro.test.FakeRunnableForTest;
import org.glassfish.concurro.test.TestContextService;
import org.glassfish.concurro.test.Util;
import org.glassfish.concurro.virtualthreads.VirtualThreadsManagedThreadFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class VirtualThreadsManagedThreadFactoryIT {

    @Test
    public void testNewThread_default() throws Exception {
        VirtualThreadsManagedThreadFactory factory = new VirtualThreadsManagedThreadFactory("test1");
        TestRunnable r = new TestRunnable();
        Thread newThread = factory.newThread(r);
        verifyThreadProperties(newThread, true, Thread.NORM_PRIORITY);
        newThread.start();
        newThread.join();
        assertFalse(r.isInterrupted);
    }

    @Test
    public void testNewThread_context() throws Exception {
        final String CLASSLOADER_NAME = "VirtualThreadsManagedThreadFactoryIT:" + new java.util.Date(System.currentTimeMillis());
        ContextSetupProvider contextSetupProvider = new ClassloaderContextSetupProvider(CLASSLOADER_NAME);
        ContextServiceImpl contextService = new TestContextService(contextSetupProvider);
        VirtualThreadsManagedThreadFactory factory = new VirtualThreadsManagedThreadFactory("test1", contextService);

        FakeRunnableForTest r = new FakeRunnableForTest(null);
        Thread newThread = factory.newThread(r);
        newThread.start();
        Util.waitForTaskComplete(r);
        r.verifyAfterRun(CLASSLOADER_NAME);
    }

    @Test
    public void testNewThread_shutdown() throws Exception {
        VirtualThreadsManagedThreadFactory factory = new VirtualThreadsManagedThreadFactory("testNewThread_shutdown");
        Runnable r = new FakeRunnableForTest(null);
        factory.stop();
        assertThrows(IllegalStateException.class, () -> factory.newThread(r));
    }

    @Test
    public void testNewThread_start_aftershutdown() throws Exception {
        VirtualThreadsManagedThreadFactory factory = new VirtualThreadsManagedThreadFactory("testNewThread_start_aftershutdown");
        TestRunnable r = new TestRunnable();
        Thread newThread = factory.newThread(r);
        assertFalse(newThread.isAlive());
        factory.stop();
        assertFalse(newThread.isAlive());
        newThread.start();
        newThread.join();
        assertTrue(r.isInterrupted);
    }

    @Test
    public void testNewThreadForkJoinPool() {
        VirtualThreadsManagedThreadFactory factory = new VirtualThreadsManagedThreadFactory("test1");
        ForkJoinPool pool = new ForkJoinPool(1);
        ForkJoinWorkerThread forkJoinWT = factory.newThread(pool);
        assertNotNull(forkJoinWT);
    }

    @Test
    public void testNewThreadForkJoinPoolContext() throws Exception {
        final String CLASSLOADER_NAME = "VirtualThreadsManagedThreadFactoryIT:" + new java.util.Date(System.currentTimeMillis());
        ContextSetupProvider contextSetupProvider = new ClassloaderContextSetupProvider(CLASSLOADER_NAME);
        ContextServiceImpl contextService = new TestContextService(contextSetupProvider);
        VirtualThreadsManagedThreadFactory factory = new VirtualThreadsManagedThreadFactory("test1", contextService);
        final long[] numbers = LongStream.rangeClosed(1, 10_000).toArray();
        String message = "starting";
        AtomicReference<String> atomicReference = new AtomicReference<>(message);
        ForkJoinPool pool = new ForkJoinPool(2, factory, null, false);
        ForkJoinTask<Long> totals = pool.submit(() -> Arrays.stream(numbers).parallel().reduce(0L, (subtotal, element) -> {
            atomicReference.compareAndSet(message, Thread.currentThread().getContextClassLoader().getName());
            return subtotal + element;
        }));
        totals.get();
        pool.shutdown();
        assertTrue(atomicReference.get().contains(CLASSLOADER_NAME));
    }

    @Test
    public void testNewThreadForkJoinPoolShutdown() throws Exception {
        VirtualThreadsManagedThreadFactory factory = new VirtualThreadsManagedThreadFactory("testNewThreadForkJoinPoolShutdown");
        ForkJoinPool pool = new ForkJoinPool(1);
        factory.stop();
        assertThrows(IllegalStateException.class, () -> factory.newThread(pool));
    }

    private void verifyThreadProperties(Thread thread, boolean isDaemon, int priority) {
        assertEquals(isDaemon, thread.isDaemon());
        assertEquals(priority, thread.getPriority());
    }

    static class TestRunnable implements Runnable {
        boolean isInterrupted = false;

        @Override
        public void run() {
          isInterrupted = Thread.currentThread().isInterrupted();
        }
    }
}
