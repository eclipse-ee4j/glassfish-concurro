/*
 * Copyright (c) 2023-2024 Contributors to the Eclipse Foundation.
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

import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;

/**
 * Abstract base class for {@code ManagedExecutorService}s, which are based on platform threads.
 *
 * @author Petr Aubrecht
 */
public abstract class AbstractPlatformThreadExecutorService extends AbstractManagedExecutorService {

    protected final ManagedThreadFactoryImpl managedThreadFactory; // FIXME aubi replace with virtual method getManagedThreadFactory

    public AbstractPlatformThreadExecutorService(String name, ManagedThreadFactoryImpl managedThreadFactory, long hungTaskThreshold, boolean longRunningTasks, ContextServiceImpl contextService, ContextSetupProvider contextCallback, RejectPolicy rejectPolicy) {
        super(name, longRunningTasks, contextService, contextCallback, rejectPolicy);

        this.managedThreadFactory = managedThreadFactory != null ? managedThreadFactory : createDefaultManagedThreadFactory(name);
        this.managedThreadFactory.setHungTaskThreshold(hungTaskThreshold);

    }

    @Override
    protected boolean isTaskHung(Thread thread, long now) {
        AbstractManagedThread managedThread = (AbstractManagedThread) thread;
        return managedThread.isTaskHung(now);
    }

    @Override
    public ManagedThreadFactoryImpl getManagedThreadFactory() {
        return managedThreadFactory;
    }

    private ManagedThreadFactoryImpl createDefaultManagedThreadFactory(String name) {
        return new ManagedThreadFactoryImpl(name + "-ManagedThreadFactory",
                null,
                Thread.NORM_PRIORITY);
    }

}
