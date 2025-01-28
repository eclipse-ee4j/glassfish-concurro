/*
 * Copyright (c) 2025 Payara Foundation and/or its affiliates.
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

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import org.glassfish.concurro.AbstractManagedExecutorService;
import org.glassfish.concurro.internal.ManagedFutureTask;

/**
 * Dummy Implementation of ManagedFutureTask for virtual threads executors.
 *
 * @author Kalin Chan
 */
public class VirtualThreadsManagedFutureTask<V> extends ManagedFutureTask<V> {

    public VirtualThreadsManagedFutureTask(AbstractManagedExecutorService executor, Runnable runnable, V result, Semaphore parallelTasksSemaphore) {
        super(executor, runnable, result);
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    public VirtualThreadsManagedFutureTask(AbstractManagedExecutorService executor, Callable<V> callable, Semaphore parallelTasksSemaphore) {
        super(executor, callable);
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public void run() {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public boolean runAndReset() {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }
}
