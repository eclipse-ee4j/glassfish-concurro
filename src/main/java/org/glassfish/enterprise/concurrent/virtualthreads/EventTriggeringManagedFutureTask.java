/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.glassfish.enterprise.concurrent.virtualthreads;

import org.glassfish.enterprise.concurrent.AbstractManagedExecutorService;
import org.glassfish.enterprise.concurrent.internal.ManagedFutureTask;

/**
 * A ManagedFutureTask that automatically triggeres events before and after executing the task.
 */
public class EventTriggeringManagedFutureTask<V> extends ManagedFutureTask<V> {

    public EventTriggeringManagedFutureTask(AbstractManagedExecutorService executor, Runnable runnable, V result) {
        super(executor, runnable, result);
    }

    @Override
    public void run() {
        this.beforeExecute(Thread.currentThread());
        Exception taskException = null;
        try {
            super.run();
        } catch (Exception e) {
            taskException = e;
            throw e;
        } finally {
            afterExecute(taskException);
        }
    }

    @Override
    public boolean runAndReset() {
        this.beforeExecute(Thread.currentThread());
        Exception taskException = null;
        try {
            return super.runAndReset();
        } catch (Exception e) {
            taskException = e;
            throw e;
        } finally {
            afterExecute(taskException);
        }
    }

    protected void beforeExecute(Thread t) {
        this.setupContext();
        this.starting(t);
    }

    protected void afterExecute(Throwable t) {
        this.done(t);
    }

}
