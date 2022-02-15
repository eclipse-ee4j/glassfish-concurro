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

import jakarta.enterprise.concurrent.ManageableThread;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;
import org.glassfish.enterprise.concurrent.test.ClassloaderContextSetupProvider;
import org.glassfish.enterprise.concurrent.test.RunnableImpl;
import org.glassfish.enterprise.concurrent.test.TestContextService;
import org.glassfish.enterprise.concurrent.test.Util;
import org.junit.Test;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

import static org.junit.Assert.*;

public class ManagedThreadFactoryImplTest {

    @Test
    public void testNewThread_default() throws Exception {
        ManagedThreadFactoryImpl factory = new ManagedThreadFactoryImpl("test1");
        TestRunnable r = new TestRunnable();
        Thread newThread = factory.newThread(r);
        verifyThreadProperties(newThread, true, Thread.NORM_PRIORITY);
        newThread.start();
        newThread.join();
        assertFalse(r.isInterrupted);
    }

    @Test
    public void testNewThread_priority_daemon() throws Exception {
        final int PRIORITY = 7;
        ContextSetupProvider callback = new ClassloaderContextSetupProvider("ManagedThreadFactoryImplTest");
        ContextServiceImpl contextService = new TestContextService(callback);
        ManagedThreadFactoryImpl factory = new ManagedThreadFactoryImpl("test1", contextService, PRIORITY);
        Runnable r = new RunnableImpl(null);
        Thread newThread = factory.newThread(r);
        verifyThreadProperties(newThread, true, PRIORITY);

        ManagedThreadFactoryImpl factory2 = new ManagedThreadFactoryImpl("test1", contextService, Thread.MIN_PRIORITY);
        newThread = factory2.newThread(r);
        verifyThreadProperties(newThread, true, Thread.MIN_PRIORITY);
    }

    @Test
    public void testNewThread_context() throws Exception {
        final String CLASSLOADER_NAME = "ManagedThreadFactoryImplTest:" + new java.util.Date(System.currentTimeMillis());
        ContextSetupProvider contextSetupProvider = new ClassloaderContextSetupProvider(CLASSLOADER_NAME);
        ContextServiceImpl contextService = new TestContextService(contextSetupProvider);
        ManagedThreadFactoryImpl factory = new ManagedThreadFactoryImpl("test1", contextService);
        RunnableImpl r = new RunnableImpl(null);
        Thread newThread = factory.newThread(r);
        newThread.start();
        Util.waitForTaskComplete(r, getLoggerName());
        r.verifyAfterRun(CLASSLOADER_NAME);
    }

    @Test (expected = IllegalStateException.class)
    public void testNewThread_shutdown() throws Exception {
        ManagedThreadFactoryImpl factory = new ManagedThreadFactoryImpl("testNewThread_shutdown");
        Runnable r = new RunnableImpl(null);
        factory.stop();
        Thread newThread = factory.newThread(r);
    }

    @Test
    public void testNewThread_start_aftershutdown() throws Exception {
        ManagedThreadFactoryImpl factory = new ManagedThreadFactoryImpl("testNewThread_start_aftershutdown");
        TestRunnable r = new TestRunnable();
        Thread newThread = factory.newThread(r);
        assertFalse(((ManageableThread)newThread).isShutdown());
        factory.stop();
        assertTrue(((ManageableThread)newThread).isShutdown());
        newThread.start();
        newThread.join();
        assertTrue(r.isInterrupted);
    }

    @Test
    public void testNewThreadForkJoinPool() throws InterruptedException {
        ManagedThreadFactoryImpl factory = new ManagedThreadFactoryImpl("test1");
        ForkJoinPool pool = new ForkJoinPool(1);
        ForkJoinWorkerThread forkJoinWT = factory.newThread(pool);
        assertNotNull(forkJoinWT);
        forkJoinWT.start();
        forkJoinWT.join();
        assertFalse(forkJoinWT.isInterrupted());
    }

    private void verifyThreadProperties(Thread thread, boolean isDaemon, int priority) {
        assertEquals(isDaemon, thread.isDaemon());
        assertEquals(priority, thread.getPriority());
    }

    private String getLoggerName() {
        return ManagedThreadFactoryImplTest.class.getName();
    }
    
    static class TestRunnable implements Runnable {
        boolean isInterrupted = false;
        
        public void run() {
          isInterrupted = Thread.currentThread().isInterrupted();  
        }
    }
}
