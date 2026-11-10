/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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

import jakarta.enterprise.concurrent.ContextService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.glassfish.concurro.AbstractManagedExecutorService.RejectPolicy;
import org.glassfish.concurro.spi.ContextHandle;
import org.glassfish.concurro.spi.ContextSetupProvider;
import org.glassfish.concurro.test.TestContextService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A task cancelled while it waits in the executor queue must not get its context set up
 * when a worker thread takes it, see issue #215.
 */
public class CancelledTaskContextTest {

    @Test
    public void cancelledPeriodicTaskDoesNotSetUpContext() throws Exception {
        StoppableContextSetupProvider provider = new StoppableContextSetupProvider();
        ManagedScheduledExecutorServiceImpl executor = new ManagedScheduledExecutorServiceImpl(
            "cancelledPeriodicTask", null, 0, false, 1, 0, TimeUnit.SECONDS, 0L,
            new TestContextService(provider), RejectPolicy.ABORT);
        try {
            CountDownLatch firstRun = new CountDownLatch(1);
            AtomicInteger runs = new AtomicInteger();
            ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> {
                runs.incrementAndGet();
                firstRun.countDown();
            }, 0, 200, TimeUnit.MILLISECONDS);
            assertTrue(firstRun.await(10, TimeUnit.SECONDS), "The task did not run");
            assertTrue(future.cancel(false), "The task was not cancelled");

            int setupsBeforeCancel = provider.setups.get();
            int runsBeforeCancel = runs.get();
            // The application that scheduled the task is gone now
            provider.stopped = true;
            // Several periods, so a worker takes the cancelled task from the queue
            Thread.sleep(1000L);

            assertEquals(runsBeforeCancel, runs.get(), "runs");
            assertEquals(setupsBeforeCancel, provider.setups.get(), "context setups");
            assertEquals(List.of(), provider.rejectedSetups, "rejected context setups");
            assertEquals(provider.setups.get(), provider.resets.get(), "context resets");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void taskCancelledInQueueDoesNotSetUpContext() throws Exception {
        StoppableContextSetupProvider provider = new StoppableContextSetupProvider();
        ManagedExecutorServiceImpl executor = new ManagedExecutorServiceImpl(
            "taskCancelledInQueue", null, 0, false, 1, 1, 0, TimeUnit.SECONDS, 0L, 10,
            new TestContextService(provider), RejectPolicy.ABORT);
        try {
            CountDownLatch blockerStarted = new CountDownLatch(1);
            CountDownLatch releaseBlocker = new CountDownLatch(1);
            Future<?> blocker = executor.submit(() -> {
                blockerStarted.countDown();
                releaseBlocker.await();
                return null;
            });
            assertTrue(blockerStarted.await(10, TimeUnit.SECONDS), "The blocking task did not start");

            AtomicInteger runs = new AtomicInteger();
            Future<?> queued = executor.submit(runs::incrementAndGet);
            assertTrue(queued.cancel(false), "The queued task was not cancelled");
            provider.stopped = true;

            releaseBlocker.countDown();
            blocker.get(10, TimeUnit.SECONDS);
            // shutdown() lets the worker drain the queue, including the cancelled task
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "The executor did not terminate");

            assertEquals(0, runs.get(), "runs");
            assertEquals(1, provider.setups.get(), "context setups");
            assertEquals(List.of(), provider.rejectedSetups, "rejected context setups");
            assertEquals(1, provider.resets.get(), "context resets");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Rejects context setup once {@link #stopped} is set, the same way an application server does
     * for an application that is no longer running.
     */
    private static final class StoppableContextSetupProvider implements ContextSetupProvider {

        private static final long serialVersionUID = 1L;

        volatile boolean stopped;
        final AtomicInteger setups = new AtomicInteger();
        final AtomicInteger resets = new AtomicInteger();
        final List<String> rejectedSetups = new CopyOnWriteArrayList<>();

        @Override
        public ContextHandle saveContext(ContextService contextService) {
            return new ContextHandle() {
                private static final long serialVersionUID = 1L;
            };
        }

        @Override
        public ContextHandle saveContext(ContextService contextService, Map<String, String> contextObjectProperties) {
            return saveContext(contextService);
        }

        @Override
        public ContextHandle setup(ContextHandle contextHandle) throws IllegalStateException {
            if (stopped) {
                rejectedSetups.add(Thread.currentThread().getName());
                throw new IllegalStateException("Application is stopped");
            }
            setups.incrementAndGet();
            return contextHandle;
        }

        @Override
        public void reset(ContextHandle contextHandle) {
            resets.incrementAndGet();
        }
    }
}
