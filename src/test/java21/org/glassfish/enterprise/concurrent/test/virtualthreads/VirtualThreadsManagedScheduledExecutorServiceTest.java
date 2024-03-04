/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.glassfish.concurro.test.virtualthreads;

import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import org.glassfish.concurro.AbstractManagedExecutorService;
import org.glassfish.concurro.ManagedScheduledExecutorServiceAdapterTest;
import org.glassfish.concurro.spi.ContextSetupProvider;
import org.glassfish.concurro.test.TestContextService;
import org.glassfish.concurro.virtualthreads.VirtualThreadsManagedScheduledExecutorService;

/**
 * Tests for VirtualThreadsManagedScheduledService.
 *
 * @author aubi
 */
public class VirtualThreadsManagedScheduledExecutorServiceTest extends ManagedScheduledExecutorServiceAdapterTest {

    /**
     * Override creation of executor for ManagedExecutorServiceAdapterTest.
     *
     * @param name
     * @param contextSetupProvider
     * @return
     */
    @Override
    protected ManagedExecutorService createManagedExecutor(String name, ContextSetupProvider contextSetupProvider) {
        VirtualThreadsManagedScheduledExecutorService mes
                = new VirtualThreadsManagedScheduledExecutorService(name, null, 0L, false,
                        1,
                        0,
                        new TestContextService(contextSetupProvider),
                        AbstractManagedExecutorService.RejectPolicy.ABORT);
        return mes.getAdapter();
    }

    /**
     * Override creation of executor for
     * ManagedScheduledExecutorServiceAdapterTest.
     *
     * @param name
     * @param contextSetupProvider
     * @return
     */
    @Override
    protected ManagedScheduledExecutorService createManagedScheduledExecutor(String name, ContextSetupProvider contextSetupProvider) {
        return (ManagedScheduledExecutorService) createManagedExecutor(name, contextSetupProvider);
    }
}
