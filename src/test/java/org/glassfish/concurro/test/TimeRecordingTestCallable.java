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

package org.glassfish.concurro.test;

import java.lang.System.Logger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static java.lang.System.Logger.Level.INFO;

/**
 * A {@link Callable} for use in scheduleAtFixedRate, scheduleWithFixedDelay, and
 * schedule with Trigger tests.
 */
public class TimeRecordingTestCallable<T> extends ManagedCallableTestTask<T> {
    private static final Logger LOG = System.getLogger(TimeRecordingTestCallable.class.getName());

    ArrayList<Long> invocations = new ArrayList<>();

    public TimeRecordingTestCallable(T result, ManagedTestTaskListener taskListener) {
        super(result, taskListener);
        invocations.add(System.currentTimeMillis());
    }

    @Override
    public T call() throws Exception {
        synchronized (invocations) {
            LOG.log(INFO, "call() executed at " + Instant.now());
            invocations.add(System.currentTimeMillis());
        }
        // sleep for 1 second
        Thread.sleep(1000);
        return super.call();
    }

    public List<Long> getInvocations() {
        synchronized(invocations) {
            return List.copyOf(invocations);
        }
    }
}

