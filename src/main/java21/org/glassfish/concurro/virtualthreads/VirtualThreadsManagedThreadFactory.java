/*
 * Copyright (c) 2023, 2024 Contributors to the Eclipse Foundation.
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
package org.glassfish.concurro.virtualthreads;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.glassfish.concurro.ContextServiceImpl;
import org.glassfish.concurro.ManagedThreadFactoryImpl;
import org.glassfish.concurro.internal.ManagedFutureTask;
import org.glassfish.concurro.internal.ThreadExpiredException;
import org.glassfish.concurro.spi.ContextHandle;

/**
 *
 * @author Ondro Mihalyi
 */
public class VirtualThreadsManagedThreadFactory extends ManagedThreadFactoryImpl {
    // map from thread to the time of task start
    // TODO replace ManagedThreadFactoryImpl.threads with startTimes.getKeySet?
    final Map<Thread, Long> startTimes = new ConcurrentHashMap<>();

    public VirtualThreadsManagedThreadFactory(String name) {
        super(name);
    }

    public VirtualThreadsManagedThreadFactory(String name, ContextServiceImpl contextService) {
        super(name, contextService);
    }

    @Override
    protected Thread createThread(Runnable taskToRun, ContextHandle contextHandleForSetup) {
        RunnableWithContext taskToRunWithContext = new RunnableWithContext(taskToRun, contextHandleForSetup);
        return Thread.ofVirtual().unstarted(taskToRunWithContext);
    }

    @Override
    protected void shutdown(Thread t) {
        if (t != null) {
            /* TODO - interrup not guaranteed to work on all JDKs if the thread hasn't started yet.
               Look how to improve this later. */
            t.interrupt();
        }

    }

    @Override
    public void taskStarting(Thread t, ManagedFutureTask task) {
        if (t != null) {
            startTimes.put(t, System.currentTimeMillis()); // TODO: use nanoTime instead?
        }
    }

    @Override
    public void taskDone(Thread t) {
        if (t != null) {
            startTimes.remove(t);
        }
    }

    public boolean isTaskHung(Thread thread, long now) {
        Long startTime = startTimes.get(thread);
        if (startTime == null) {
            return false;
        }
        return now - startTime > getHungTaskThreshold();
    }

    static private final System.Logger loggerForRunnableWithContext = System.getLogger(RunnableWithContext.class.getName());

    private final class RunnableWithContext implements Runnable {

        private final Runnable nestedRunnable;
        private final ContextHandle contextHandleForSetup;
        private final System.Logger logger = loggerForRunnableWithContext;

        public RunnableWithContext(Runnable nestedRunnable, ContextHandle contextHandleForSetup) {
            this.nestedRunnable = nestedRunnable;
            this.contextHandleForSetup = contextHandleForSetup;
        }

        @Override
        public void run() {
            ContextHandle handle = null;
            try {
                if (contextHandleForSetup != null) {
                    handle = getContextSetupProvider().setup(contextHandleForSetup);
                }
                nestedRunnable.run();
            } catch (ThreadExpiredException ex) {
                logger.log(INFO, ex.toString());
            } catch (Throwable t) {
                logger.log(ERROR, getName(), t);
            } finally {
                if (handle != null) {
                    getContextSetupProvider().reset(handle);
                }
                removeThread(Thread.currentThread());
            }
        }

    }

}
