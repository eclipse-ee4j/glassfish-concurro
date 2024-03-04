/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 Payara Foundation and/or its affiliates.
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

import java.util.concurrent.TimeUnit;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import org.glassfish.concurro.ManagedScheduledExecutorServiceImpl;
import org.glassfish.concurro.AbstractManagedExecutorService.RejectPolicy;
import org.glassfish.concurro.spi.ContextSetupProvider;
import org.glassfish.concurro.test.TestContextService;

/**
 * Tests for Life cycle APIs in ManagedScheduledExecutorServiceImpl
 */
public class ManagedScheduledExecutorServiceImplTest extends ManagedExecutorServiceImplTest {

    @Override
    protected ManagedExecutorService createManagedExecutor(String name, ContextSetupProvider contextCallback) {
        return new ManagedScheduledExecutorServiceImpl(name, null, 0, false,
                    1,  
                    0, TimeUnit.SECONDS,
                    0L,
                    new TestContextService(contextCallback),
                    RejectPolicy.ABORT);
    }

}
