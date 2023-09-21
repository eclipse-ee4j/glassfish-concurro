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

import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.concurrent.ManagedTaskListener;
import java.util.concurrent.Future;

/**
 * A listener that delegates events to multiple listeners in the order in which they are added to this listener. If any
 * delegated listener throws an exception, the MultiManagedTaskListener stops execution of other listeners and throws
 * the exception. If any listener is null, it's ignored.
 *
 * @author Ondro Mihalyi
 */
public class MultiManagedTaskListener implements ManagedTaskListener {

    private ManagedTaskListener[] delegates;

    public MultiManagedTaskListener(ManagedTaskListener... delegates) {
        this.delegates = delegates;
    }

    @Override
    public void taskSubmitted(Future<?> future, ManagedExecutorService executor, Object task) {
        for (ManagedTaskListener listener : delegates) {
            if (listener != null) {
                listener.taskSubmitted(future, executor, task);
            }
        }
    }

    @Override
    public void taskAborted(Future<?> future, ManagedExecutorService executor, Object task, Throwable exception) {
        for (ManagedTaskListener listener : delegates) {
            if (listener != null) {
                listener.taskAborted(future, executor, task, exception);
            }
        }
    }

    @Override
    public void taskDone(Future<?> future, ManagedExecutorService executor, Object task, Throwable exception) {
        for (ManagedTaskListener listener : delegates) {
            if (listener != null) {
                listener.taskDone(future, executor, task, exception);
            }
        }
    }

    @Override
    public void taskStarting(Future<?> future, ManagedExecutorService executor, Object task) {
        for (ManagedTaskListener listener : delegates) {
            if (listener != null) {
                listener.taskStarting(future, executor, task);
            }
        }
    }

}
