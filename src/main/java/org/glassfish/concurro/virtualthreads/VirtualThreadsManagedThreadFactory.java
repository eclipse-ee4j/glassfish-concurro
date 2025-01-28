/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
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

import org.glassfish.concurro.ContextServiceImpl;
import org.glassfish.concurro.ManagedThreadFactoryImpl;
import org.glassfish.concurro.internal.ManagedFutureTask;
import org.glassfish.concurro.spi.ContextHandle;

/**
 * Dummy Implementation of ManagedThreadFactory using Virtual Threads.
 * @author Kalin Chan
 */
public class VirtualThreadsManagedThreadFactory extends ManagedThreadFactoryImpl {

    public VirtualThreadsManagedThreadFactory(String name) {
        super(null);
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    public VirtualThreadsManagedThreadFactory(String name, ContextServiceImpl contextService) {
        super(null, null);
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");

    }

    @Override
    protected Thread createThread(Runnable taskToRun, ContextHandle contextHandleForSetup) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    protected void shutdown(Thread t) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }

    @Override
    public void taskStarting(Thread t, ManagedFutureTask task) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");

    }

    @Override
    public void taskDone(Thread t) {
        throw new UnsupportedOperationException("This feature is only supported for Java 21+");
    }
}
