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

import java.util.List;
import java.util.concurrent.TimeUnit;
import jakarta.enterprise.concurrent.ManagedExecutorService;

/**
 * Abstract base class for {@code ManagedExecutorService} and 
 * {@code ManagedScheduledExecutorService}
 * implementation with life cycle operations disabled for handing out to 
 * application components.
 */
public abstract class AbstractManagedExecutorServiceAdapter implements ManagedExecutorService {

    protected static final String LIFECYCLE_OPER_NOT_SUPPORTED = "Lifecycle operation not supported";

    @Override
    public void shutdown() {
        throw new IllegalStateException(LIFECYCLE_OPER_NOT_SUPPORTED);
    }

    @Override
    public List<Runnable> shutdownNow() {
        throw new IllegalStateException(LIFECYCLE_OPER_NOT_SUPPORTED);
    }

    @Override
    public boolean isShutdown() {
        throw new IllegalStateException(LIFECYCLE_OPER_NOT_SUPPORTED);
    }

    @Override
    public boolean isTerminated() {
        throw new IllegalStateException(LIFECYCLE_OPER_NOT_SUPPORTED);
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        throw new IllegalStateException(LIFECYCLE_OPER_NOT_SUPPORTED);
    }
    
}
