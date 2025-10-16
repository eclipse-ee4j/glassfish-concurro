/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
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

import jakarta.enterprise.concurrent.ManageableThread;

/**
 * Abstract base class for threads to be returned by
 * {@link ManagedThreadFactoryImpl#createThread(java.lang.Runnable, org.glassfish.concurro.spi.ContextHandle) }
 * @author alai
 */
public abstract class AbstractManagedThread extends Thread implements ManageableThread {
    private volatile boolean shutdown;

    public AbstractManagedThread(Runnable target) {
        super(target);
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * Marks the thread for shutdown so application components could
     * check the status of this thread and finish any work as soon
     * as possible.
     */
    public void shutdown() {
        shutdown = true;
    }

    abstract boolean cancelTask();

    /**
     * Return the identity name of the task that is being run on this thread.
     *
     * @return The identity name of the task that is being run on this thread, or
     * "null" if there is none.
     */
    abstract public String getTaskIdentityName();

    /**
     * Return the time in millisecond since the task has started.
     *
     * @param now The current time in milliseconds, which is typically obtained
     *            by calling {@link System#currentTimeMillis() }
     *
     * @return The time since the task has started in milliseconds.
     */
    abstract public long getTaskRunTime(long now);

    /**
     * Return the time that the thread was started, measured in milliseconds,
     * between the current time and midnight, January 1, 1970 UTC.
     *
     * @return The time that the thread was started, in milliseconds.
     */
    public abstract long getThreadStartTime();

    abstract boolean isTaskHung(long now);

}
